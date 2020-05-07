package com.wavefront.spring.autoconfigure;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.handler.MutableSpan.AnnotationConsumer;
import brave.handler.MutableSpan.TagConsumer;
import brave.propagation.TraceContext;
import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.sdk.appagent.jvm.reporter.WavefrontJvmReporter;
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
import static com.wavefront.spring.autoconfigure.SpanDerivedMetricsUtils.TRACING_DERIVED_PREFIX;
import static com.wavefront.spring.autoconfigure.SpanDerivedMetricsUtils.reportHeartbeats;
import static com.wavefront.spring.autoconfigure.SpanDerivedMetricsUtils.reportWavefrontGeneratedData;

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
final class WavefrontSleuthSpanHandler extends FinishedSpanHandler implements Runnable, Closeable {
  private static final Log LOG = LogFactory.getLog(WavefrontSleuthSpanHandler.class);

  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L114-L114
  private static final String DEFAULT_SPAN_NAME = "defaultOperation";

  private final static String DEFAULT_SOURCE = "wavefront-spring-boot";
  private final static String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";

  final LinkedBlockingQueue<Pair<TraceContext, MutableSpan>> spanBuffer;
  final WavefrontSender wavefrontSender;
  final WavefrontInternalReporter wfInternalReporter;
  @Nullable
  final WavefrontJvmReporter wfJvmReporter;
  final Set<String> traceDerivedCustomTagKeys;
  final Counter spansDropped;
  final Counter spansReceived;
  final Counter reportErrors;
  final Thread sendingThread;

  private volatile boolean stop = false;
  private final ConcurrentMap<HeartbeatMetricKey, Boolean> discoveredHeartbeatMetrics;
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
    this.discoveredHeartbeatMetrics = new ConcurrentHashMap<>();

    this.heartbeatMetricsScheduledExecutorService = Executors.newScheduledThreadPool(1,
        runnable -> {
          Thread thread = new NamedThreadFactory("sleuth-heart-beater").
              newThread(runnable);
          thread.setDaemon(true);
          return thread;
        });

    // Emit Heartbeats Metrics every 1 min.
    heartbeatMetricsScheduledExecutorService.scheduleAtFixedRate(() -> {
      try {
        reportHeartbeats(WAVEFRONT_GENERATED_COMPONENT, wavefrontSender, discoveredHeartbeatMetrics);
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

    if (wavefrontProperties.getTracing().isExtractJvmMetrics()) {
      wfJvmReporter = new WavefrontJvmReporter.Builder(applicationTags).
          withSource(source).build(wavefrontSender);
      // Start the JVM reporter
      wfJvmReporter.start();
    } else {
      wfJvmReporter = null;
    }

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
  @Override public boolean handle(TraceContext context, MutableSpan span) {
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
    long durationMillis = startMillis != 0 && finishMillis != 0L ? Math.max(finishMillis - startMillis, 1L) : 0L;

    WavefrontConsumer wavefrontConsumer = new WavefrontConsumer(defaultTagKeys);

    // If MutableSpan.tags["error"] is here, it was from a layered api, instrumentation or the user.
    // In other words MutableSpan.error() != null does not mean MutableSpan.tags["error"] != null
    //
    // MutableSpan.error() could be recorded without MutableSpan.tags["error"]
    // Ex 1. brave.Span.error(new OutOfMemoryError()) -> MutableSpan.error(new OutOfMemoryError())
    // Ex 2. brave.Span.error(new RpcException()) -> MutableSpan.error(new RpcException())
    // Ex 3. brave.Span.error(new NullPointerException()) -> MutableSpan.error(new NullPointerException())
    //
    // The above are examples of exceptions that users typically do not process, so are unlikely to
    // parse into an "error" tag. The opposite is also true as not all errors are derived from
    // Throwables. Particularly, RPC frameworks often do not use exceptions as error signals.
    //
    // MutableSpan.tags["error"] could be recorded without MutableSpan.error()
    // Ex 1. io.opentracing.Span.tag(ERROR, true) -> MutableSpan.tag("error", "true")
    // Ex 2. brave.SpanCustomizer.tag("error", "") -> MutableSpan.tag("error", "")
    // Ex 3. brave.Span.tag("error", "CANCELLED") -> MutableSpan.tag("error", "CANCELLED")
    //
    // The above examples are using in-band apis in Brave. FinishedSpanHandler is after the fact.
    // Since MutableSpan.tags["error"] isn't defaulted, handlers like here can tell the difference
    // between explicitly set error messages, and what's needed by their format. It may not be
    // obvious that MutableSpan.error() exists for custom formats including metrics.
    //
    // Ex. 1. MutableSpan.tag("error", "") to redact the error message from Zipkin
    // Ex. 2. MutableSpan.error() -> MutableSpan.tags["exception"] to match metrics dimension
    // Ex. 3. MutableSpan.error() -> CustomFormat.stackTrace for sophisticated trace formats.
    //
    // Until we know more about Wavefront's backend data requirements, We only set an error bit.
    // Specifically, we set isError when either of the following are not null:
    //  * MutableSpan.error()
    //  * MutableSpan.tags["error"]
    wavefrontConsumer.isError = span.error() != null;

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L397-L402
    List<SpanLog> spanLogs = new ArrayList<>();
    span.forEachAnnotation(wavefrontConsumer, spanLogs);

    List<Pair<String, String>> tags = new ArrayList<>(defaultTags);
    // Check for span.error() for uncaught exception in request mapping and add it to Wavefront span tag
    if (span.error() != null && span.tag("error") == null) {
      tags.add(Pair.of("error", "true"));
    }
    span.forEachTag(wavefrontConsumer, tags);

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L300-L303
    if (context.debug() || wavefrontConsumer.debug) {
      tags.add(Pair.of(DEBUG_TAG_KEY, "true"));
    }

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L254-L266
    if (span.kind() != null) {
      String kind = span.kind().toString().toLowerCase();
      tags.add(Pair.of("span.kind", kind));
      if (wavefrontConsumer.hasAnnotations) {
        tags.add(Pair.of("_spanSecondaryId", kind));
      }
    }

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L329-L332
    if (wavefrontConsumer.hasAnnotations) {
      tags.add(Pair.of(SPAN_LOG_KEY, "true"));
    }

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L324-L327
    if (span.localIp() != null) {
      tags.add(Pair.of("ipv4", span.localIp())); // NOTE: this could be IPv6!!
    }

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
        discoveredHeartbeatMetrics.putIfAbsent(reportWavefrontGeneratedData(wfInternalReporter,
            name, applicationTags.getApplication(), applicationTags.getService(),
            applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster(),
            applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard(),
            source, wavefrontConsumer.componentTagValue, wavefrontConsumer.isError, durationMillis,
            this.traceDerivedCustomTagKeys, tags), true);
      } catch (RuntimeException t) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("error sending span RED metrics " + context, t);
        }
      }
    }
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

  static class WavefrontConsumer
      implements AnnotationConsumer<List<SpanLog>>, TagConsumer<List<Pair<String, String>>> {
    final Set<String> defaultTagKeys;
    boolean debug, hasAnnotations, isError;
    String componentTagValue = NULL_TAG_VAL;

    WavefrontConsumer(Set<String> defaultTagKeys) {
      this.defaultTagKeys = defaultTagKeys;
    }

    @Override public void accept(List<SpanLog> target, long timestamp, String value) {
      hasAnnotations = true;
      target.add(new SpanLog(timestamp, Collections.singletonMap("annotation", value)));
    }

    @Override public void accept(List<Pair<String, String>> target, String key, String value) {
      if (value.isEmpty()) return;
      String lcKey = key.toLowerCase(Locale.ROOT);
      if (defaultTagKeys.contains(lcKey)) return;
      if (lcKey.equals(DEBUG_TAG_KEY)) {
        debug = true; // This tag is set out-of-band
        return;
      }
      if (lcKey.equals(ERROR_TAG_KEY)) {
        value = "true"; // Ignore the original error value
        isError = true;
      }
      if (lcKey.equals(COMPONENT_TAG_KEY)) {
        componentTagValue = value;
      }
      target.add(Pair.of(key, value));
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
