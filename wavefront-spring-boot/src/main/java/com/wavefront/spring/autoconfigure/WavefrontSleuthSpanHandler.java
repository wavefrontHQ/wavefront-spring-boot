package com.wavefront.spring.autoconfigure;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import brave.handler.MutableSpan;
import brave.handler.SpanHandler;
import brave.propagation.TraceContext;
import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.java_sdk.com.google.common.collect.Sets;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.spring.autoconfigure.WavefrontProperties.Application;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.wavefront.internal.SpanDerivedMetricsUtils.TRACING_DERIVED_PREFIX;
import static com.wavefront.internal.SpanDerivedMetricsUtils.reportHeartbeats;
import static com.wavefront.internal.SpanDerivedMetricsUtils.reportWavefrontGeneratedData;
import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.COMPONENT_TAG_KEY;
import static com.wavefront.sdk.common.Constants.DEBUG_TAG_KEY;
import static com.wavefront.sdk.common.Constants.ERROR_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SOURCE_KEY;
import static com.wavefront.sdk.common.Constants.SPAN_LOG_KEY;

/**
 * This converts a span recorded by Brave and invokes {@link WavefrontSender#sendSpan}.
 *
 * <p>This uses a combination of conversion approaches from Wavefront projects:
 * <ul>
 *   <li>https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java</li>
 *   <li>https://github.com/wavefrontHQ/wavefront-proxy</li>
 * </ul>
 *
 * <p>On conflict, we make a comment and prefer wavefront-opentracing-sdk-java. The rationale is
 * wavefront-opentracing-sdk-java uses the same {@link WavefrontSender#sendSpan} library,
 * so it is easier to reason with. This policy can be revisited by future maintainers.
 *
 * <p><em>Note:</em>UUID conversions follow the same conventions used in practice in Wavefront.
 * Ex. https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/6babf2ff95daa37452e1e8c35ae54b58b6abb50f/src/main/java/com/wavefront/opentracing/propagation/JaegerWavefrontPropagator.java#L191-L204
 * While in practice this is not a problem, it is worth mentioning that this convention will only
 * only result in RFC 4122 timestamp (version 1) format by accident. In other words, don't call
 * {@link UUID#timestamp()} on UUIDs converted here, or in other Wavefront code, as it might
 * throw.
 */
final class WavefrontSleuthSpanHandler extends SpanHandler implements Runnable, Closeable {
  private static final Log LOG = LogFactory.getLog(WavefrontSleuthSpanHandler.class);

  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L114-L114
  private static final String DEFAULT_SPAN_NAME = "defaultOperation";

  private final static String DEFAULT_SOURCE = "wavefront-spring-boot";
  private final static String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";

  final LinkedBlockingQueue<Pair<TraceContext, MutableSpan>> spanBuffer;
  final WavefrontSender wavefrontSender;
  final WavefrontInternalReporter wfInternalReporter;
  final Set<String> traceDerivedCustomTagKeys;
  final Counter spansDropped;
  final Counter spansReceived;
  final Counter reportErrors;
  final Thread sendingThread;

  private volatile boolean stop = false;
  private final Set<Pair<Map<String, String>, String>> discoveredHeartbeatMetrics;
  private final ScheduledExecutorService heartbeatMetricsScheduledExecutorService;

  final String source;
  final List<Pair<String, String>> defaultTags;
  final Set<String> defaultTagKeys;
  final ApplicationTags applicationTags;

  WavefrontSleuthSpanHandler(int maxQueueSize, WavefrontSender wavefrontSender,
                             MeterRegistry meterRegistry, String source,
                             ApplicationTags applicationTags,
                             WavefrontProperties wavefrontProperties,
                             String localServiceName) {
    this.wavefrontSender = wavefrontSender;
    this.applicationTags = applicationTags;
    this.discoveredHeartbeatMetrics = Sets.newConcurrentHashSet();

    this.heartbeatMetricsScheduledExecutorService = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("sleuth-heart-beater").setDaemon(true));

    // Emit Heartbeats Metrics every 1 min.
    heartbeatMetricsScheduledExecutorService.scheduleAtFixedRate(() -> {
      try {
        reportHeartbeats(wavefrontSender, discoveredHeartbeatMetrics, WAVEFRONT_GENERATED_COMPONENT);
      } catch (IOException e) {
        LOG.warn("Cannot report heartbeat metric to wavefront");
      }
    }, 1, 60, TimeUnit.SECONDS);

    this.traceDerivedCustomTagKeys = new HashSet<>(
        wavefrontProperties.getTracing().getRedMetricsCustomTagKeys());

    // Start the reporter
    wfInternalReporter = new WavefrontInternalReporter.Builder().
        prefixedWith(TRACING_DERIVED_PREFIX).withSource(DEFAULT_SOURCE).reportMinuteDistribution().
        build(wavefrontSender);
    wfInternalReporter.start(1, TimeUnit.MINUTES);

    this.source = source;
    this.defaultTags = createDefaultTags(applicationTags, localServiceName);
    this.defaultTagKeys = defaultTags.stream().map(p -> p._1).collect(Collectors.toSet());
    this.defaultTagKeys.add(SOURCE_KEY);

    this.spanBuffer = new LinkedBlockingQueue<>(maxQueueSize);

    // init internal metrics
    meterRegistry.gauge("reporter.queue.size", spanBuffer, sb -> (double) sb.size());
    meterRegistry.gauge("reporter.queue.remaining_capacity", spanBuffer,
        sb -> (double) sb.remainingCapacity());
    this.spansReceived = meterRegistry.counter("reporter.spans.received");
    this.spansDropped = meterRegistry.counter("reporter.spans.dropped");
    this.reportErrors = meterRegistry.counter("reporter.errors");

    this.sendingThread = new Thread(this, "wavefrontSpanReporter");
    this.sendingThread.setDaemon(true);
    this.sendingThread.start();
  }

  // Exact same behavior as WavefrontSpanReporter
  // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L163-L179
  @Override public boolean end(TraceContext context, MutableSpan span, Cause cause) {
    spansReceived.increment();
    if (!spanBuffer.offer(Pair.of(context, span))) {
      spansDropped.increment();
      if (LOG.isWarnEnabled()) {
        LOG.warn("Buffer full, dropping span: " + span);
        LOG.warn("Total spans dropped: " + spansDropped.count());
      }
    }
    return true; // regardless of error, other handlers should run
  }

  private void send(TraceContext context, MutableSpan span) {
    UUID traceId = new UUID(context.traceIdHigh(), context.traceId());
    UUID spanId = new UUID(0L, context.spanId());

    // NOTE: wavefront-opentracing-sdk-java and wavefront-proxy differ, but we prefer the former.
    // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L187-L190
    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L248-L252
    List<UUID> parents = null;
    if (context.parentIdAsLong() != 0L) {
      parents = Collections.singletonList(new UUID(0L, context.parentIdAsLong()));
    }
    List<UUID> followsFrom = null;

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L344-L345
    String name = span.name();
    if (name == null) name = DEFAULT_SPAN_NAME;

    // Start and duration become 0L if unset. Any positive duration rounds up to 1 millis.
    long startMillis = span.startTimestamp() / 1000L, finishMillis = span.finishTimestamp() / 1000L;
    long durationMicros = span.finishTimestamp() - span.startTimestamp();
    long durationMillis = startMillis != 0 && finishMillis != 0L ? Math.max(finishMillis - startMillis, 1L) : 0L;

    List<SpanLog> spanLogs = convertAnnotationsToSpanLogs(span);
    TagList tags = new TagList(defaultTagKeys, defaultTags, context, span);

    try {
      wavefrontSender.sendSpan(name, startMillis, durationMillis, source, traceId, spanId,
          parents, followsFrom, tags, spanLogs);
    } catch (IOException | RuntimeException t) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("error sending span " + context, t);
      }
    }

    // report stats irrespective of span sampling.
    if (wfInternalReporter != null) {
      // report converted metrics/histograms from the span
      try {
        discoveredHeartbeatMetrics.add(reportWavefrontGeneratedData(wfInternalReporter,
            name, applicationTags.getApplication(), applicationTags.getService(),
            applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster(),
            applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard(),
            source, tags.componentTagValue, tags.isError, durationMicros,
            traceDerivedCustomTagKeys, tags));
      } catch (RuntimeException t) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("error sending span RED metrics " + context, t);
        }
      }
    }
  }

  /**
   * Extracted for test isolation and as parsing otherwise implies multiple-returns or scanning
   * later.
   *
   * <p>Ex. {@code SpanDerivedMetricsUtils#reportWavefrontGeneratedData} needs tags separately from
   * the component tag and error status.
   */
  static final class TagList extends ArrayList<Pair<String, String>> {
    String componentTagValue = NULL_TAG_VAL;
    boolean isError; // See explanation here: https://github.com/openzipkin/brave/pull/1221

    TagList(
        Set<String> defaultTagKeys,
        List<Pair<String, String>> defaultTags,
        TraceContext context,
        MutableSpan span
    ){
      super(defaultTags.size() + span.tagCount());
      boolean debug = context.debug(), hasAnnotations = span.annotationCount() > 0;
      isError = span.error() != null;

      int tagCount = span.tagCount();
      addAll(defaultTags);
      for (int i = 0; i < tagCount; i++) {
        String key = span.tagKeyAt(i), value = span.tagValueAt(i);
        String lcKey = key.toLowerCase(Locale.ROOT);
        if (lcKey.equals(ERROR_TAG_KEY)) {
          isError = true;
          continue; // We later replace whatever the potentially empty value was with "true"
        }
        if (value.isEmpty()) continue;
        if (defaultTagKeys.contains(lcKey)) continue;
        if (lcKey.equals(DEBUG_TAG_KEY)) {
          debug = true; // This tag is set out-of-band
          continue;
        }
        if (lcKey.equals(COMPONENT_TAG_KEY)) {
          componentTagValue = value;
        }
        add(Pair.of(key, value));
      }

      // Check for span.error() for uncaught exception in request mapping and add it to Wavefront span tag
      if (isError) add(Pair.of("error", "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L300-L303
      if (debug) add(Pair.of(DEBUG_TAG_KEY, "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L254-L266
      if (span.kind() != null) {
        String kind = span.kind().toString().toLowerCase();
        add(Pair.of("span.kind", kind));
        if (hasAnnotations) {
          add(Pair.of("_spanSecondaryId", kind));
        }
      }

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L329-L332
      if (hasAnnotations) add(Pair.of(SPAN_LOG_KEY, "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L324-L327
      if (span.localIp() != null) {
        add(Pair.of("ipv4", span.localIp())); // NOTE: this could be IPv6!!
      }
    }
  }

  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L397-L402
  static List<SpanLog> convertAnnotationsToSpanLogs(MutableSpan span) {
    int annotationCount = span.annotationCount();
    if (annotationCount == 0) return Collections.emptyList();
    List<SpanLog> spanLogs = new ArrayList<>(annotationCount);
    for (int i = 0; i < annotationCount; i++) {
      long epochMicros = span.annotationTimestampAt(i);
      String value = span.annotationValueAt(i);
      spanLogs.add(new SpanLog(epochMicros, Collections.singletonMap("annotation", value)));
    }
    return spanLogs;
  }

  @Override public void run() {
    while (!stop) {
      try {
        Pair<TraceContext, MutableSpan> contextAndSpan = spanBuffer.take();
        send(contextAndSpan._1, contextAndSpan._2);
      } catch (InterruptedException ex) {
        if (LOG.isInfoEnabled()) {
          LOG.info("reporting thread interrupted");
        }
      } catch (Throwable ex) {
        LOG.warn("Error processing buffer", ex);
      }
    }
  }

  @Override public void close() {
    stop = true;
    try {
      // wait for 5 secs max
      sendingThread.join(5000);
      heartbeatMetricsScheduledExecutorService.shutdownNow();
    } catch (InterruptedException ex) {
      // no-op
    }
  }

  // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/WavefrontTracer.java#L275-L280
  static List<Pair<String, String>> createDefaultTags(ApplicationTags applicationTags,
                                                      String serviceName) {
    List<Pair<String, String>> result = new ArrayList<>();
    result.add(Pair.of(APPLICATION_TAG_KEY, applicationTags.getApplication()));
    // Prefer the user's service name unless they overwrote it with the wavefront property
    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L263-L266
    if (!Objects.equals(applicationTags.getService(), Application.DEFAULT_SERVICE_NAME)) {
      result.add(Pair.of(SERVICE_TAG_KEY, applicationTags.getService()));
    }
    else {
      result.add(Pair.of(SERVICE_TAG_KEY, serviceName));
    }
    result.add(Pair.of(CLUSTER_TAG_KEY,
        applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster()));
    result.add(Pair.of(SHARD_TAG_KEY,
        applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard()));
    if (applicationTags.getCustomTags() != null) {
      applicationTags.getCustomTags().forEach((k, v) -> result.add(Pair.of(k, v)));
    }
    return result;
  }
}
