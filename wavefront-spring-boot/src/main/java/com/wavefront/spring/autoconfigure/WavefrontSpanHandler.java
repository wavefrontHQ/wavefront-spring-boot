package com.wavefront.spring.autoconfigure;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.wavefront.internal.reporter.WavefrontInternalReporter;
import com.wavefront.sdk.common.NamedThreadFactory;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.SpanLog;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.exporter.FinishedSpan;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.util.StringUtils;

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
 *   <li><a href="https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java">...</a></li>
 *   <li><a href="https://github.com/wavefrontHQ/wavefront-proxy">...</a></li>
 * </ul>
 *
 * <p>On conflict, we make a comment and prefer wavefront-opentracing-sdk-java. The rationale is
 * wavefront-opentracing-sdk-java uses the same {@link WavefrontSender#sendSpan} library,
 * so it is easier to reason with. This policy can be revisited by future maintainers.
 *
 * <p><em>Note:</em>UUID conversions follow the same conventions used in practice in Wavefront.
 * Ex. <a href="https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/6babf2ff95daa37452e1e8c35ae54b58b6abb50f/src/main/java/com/wavefront/opentracing/propagation/JaegerWavefrontPropagator.java#L191-L204">...</a>
 * While in practice this is not a problem, it is worth mentioning that this convention will only
 * result in RFC 4122 timestamp (version 1) format by accident. In other words, don't call
 * {@link UUID#timestamp()} on UUIDs converted here, or in other Wavefront code, as it might
 * throw.
 *
 * @author Adrian Cole
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @author Glenn Oppegard
 *
 */
public final class WavefrontSpanHandler implements Runnable, Closeable {
  private static final Log LOG = LogFactory.getLog(WavefrontSpanHandler.class);

  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L114-L114
  private static final String DEFAULT_SPAN_NAME = "defaultOperation";

  private static final String DEFAULT_SOURCE = "wavefront-spring-boot";

  private static final String WAVEFRONT_GENERATED_COMPONENT = "wavefront-generated";

  private static final int LONG_BYTES = Long.SIZE / Byte.SIZE;

  private static final int BYTE_BASE16 = 2;

  private static final int LONG_BASE16 = BYTE_BASE16 * LONG_BYTES;

  private static final int TRACE_ID_HEX_SIZE = 2 * LONG_BASE16;

  private static final String ALPHABET = "0123456789abcdef";

  private static final int ASCII_CHARACTERS = 128;

  private static final byte[] DECODING = buildDecodingArray();

  private final LinkedBlockingQueue<SpanToSend> spanBuffer;

  private final WavefrontSender wavefrontSender;

  private final WavefrontInternalReporter wfInternalReporter;

  private final Set<String> traceDerivedCustomTagKeys;

  private final Thread sendingThread;

  private final Set<Pair<Map<String, String>, String>> discoveredHeartbeatMetrics;

  private final ScheduledExecutorService heartbeatMetricsScheduledExecutorService;

  private final String source;

  private final List<Pair<String, String>> defaultTags;

  private final Set<String> defaultTagKeys;

  private final ApplicationTags applicationTags;

  private final SpanMetrics spanMetrics;

  private final AtomicBoolean stop = new AtomicBoolean();


  WavefrontSpanHandler(int maxQueueSize, WavefrontSender wavefrontSender, SpanMetrics spanMetrics,
                       String source, ApplicationTags applicationTags,
                       Set<String> redMetricsCustomTagKeys) {
    this.wavefrontSender = wavefrontSender;
    this.spanMetrics = spanMetrics;
    this.applicationTags = applicationTags;
    this.discoveredHeartbeatMetrics = ConcurrentHashMap.newKeySet();

    this.heartbeatMetricsScheduledExecutorService = Executors.newScheduledThreadPool(1,
        new NamedThreadFactory("wavefront-boot-heart-beater").setDaemon(true));

    // Emit Heartbeats Metrics every 1 min.
    heartbeatMetricsScheduledExecutorService.scheduleAtFixedRate(() -> {
      try {
        reportHeartbeats(wavefrontSender, discoveredHeartbeatMetrics, WAVEFRONT_GENERATED_COMPONENT);
      } catch (IOException e) {
        LOG.warn("Cannot report heartbeat metric to wavefront");
      }
    }, 1, 60, TimeUnit.SECONDS);

    this.traceDerivedCustomTagKeys = new HashSet<>(redMetricsCustomTagKeys);

    // Start the reporter
    wfInternalReporter = new WavefrontInternalReporter.Builder().
        prefixedWith(TRACING_DERIVED_PREFIX).withSource(DEFAULT_SOURCE).reportMinuteDistribution().
        build(wavefrontSender);
    wfInternalReporter.start(1, TimeUnit.MINUTES);

    this.source = source;
    this.defaultTags = createDefaultTags(applicationTags);
    this.defaultTagKeys = defaultTags.stream().map(p -> p._1).collect(Collectors.toSet());
    this.defaultTagKeys.add(SOURCE_KEY);

    this.spanBuffer = new LinkedBlockingQueue<>(maxQueueSize);

    spanMetrics.registerQueueSize(spanBuffer);
    spanMetrics.registerQueueRemainingCapacity(spanBuffer);

    this.sendingThread = new Thread(this, "wavefrontSpanReporter");
    this.sendingThread.setDaemon(true);
    this.sendingThread.start();
  }

  // Exact same behavior as WavefrontSpanReporter
  // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L163-L179
  public boolean end(TraceContext context, FinishedSpan span) {
    this.spanMetrics.reportReceived();
    if (!spanBuffer.offer(new SpanToSend(context, span))) {
      this.spanMetrics.reportDropped();
      if (LOG.isWarnEnabled()) {
        LOG.warn("Buffer full, dropping span: " + span);
        LOG.warn("Total spans dropped: " + this.spanMetrics.getDropped());
      }
    }
    return true; // regardless of error, other handlers should run
  }

  List<Pair<String, String>> getDefaultTags() {
    return Collections.unmodifiableList(this.defaultTags);
  }

  private String padToTraceIdHexSize(String string) {
    if (string.length() >= TRACE_ID_HEX_SIZE) {
      return string;
    }
    else {
      return "0".repeat(TRACE_ID_HEX_SIZE - string.length()).concat(string);
    }
  }

  private void send(TraceContext context, FinishedSpan span) {
    String traceIdString = padToTraceIdHexSize(context.traceId());
    String traceIdHigh = traceIdString.substring(0, traceIdString.length() / 2);
    String traceIdLow = traceIdString.substring(traceIdString.length() / 2);
    UUID traceId = new UUID(longFromBase16String(traceIdHigh), longFromBase16String(traceIdLow));
    UUID spanId = new UUID(0L, longFromBase16String(context.spanId()));

    // NOTE: wavefront-opentracing-sdk-java and wavefront-proxy differ, but we prefer the former.
    // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L187-L190
    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L248-L252
    List<UUID> parents = null;
    String parentId = context.parentId();
    if (StringUtils.hasText(parentId) && longFromBase16String(parentId) != 0L) {
      parents = Collections.singletonList(new UUID(0L, longFromBase16String(parentId)));
    }

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L344-L345
    String name = span.getName();
    if (name == null) name = DEFAULT_SPAN_NAME;


    // Start and duration become 0L if unset. Any positive duration rounds up to 1 millis.
    long startMillis = span.getStartTimestamp().toEpochMilli();
    long finishMillis = span.getEndTimestamp().toEpochMilli();
    long durationMicros = ChronoUnit.MICROS.between(span.getStartTimestamp(), span.getEndTimestamp());
    long durationMillis = startMillis != 0 && finishMillis != 0L ? Math.max(finishMillis - startMillis, 1L) : 0L;

    List<SpanLog> spanLogs = convertAnnotationsToSpanLogs(span);
    TagList tags = new TagList(defaultTagKeys, defaultTags, span);

    try {
      List<UUID> followsFrom = null;
      //noinspection ConstantConditions
      wavefrontSender.sendSpan(name, startMillis, durationMillis, source, traceId, spanId,
          parents, followsFrom, tags, spanLogs);
    } catch (IOException | RuntimeException t) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("error sending span " + context, t);
      }
      this.spanMetrics.reportErrors();
    }

    // report stats irrespective of span sampling.
    if (wfInternalReporter != null) {
      // report converted metrics/histograms from the span
      try {
        discoveredHeartbeatMetrics.add(reportWavefrontGeneratedData(wfInternalReporter, name,
            applicationTags.getApplication(), applicationTags.getService(),
            applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster(),
            applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard(),
            source, tags.componentTagValue, tags.isError, durationMicros, traceDerivedCustomTagKeys,
            tags));
      }
      catch (RuntimeException t) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("error sending span RED metrics " + context, t);
        }
        this.spanMetrics.reportErrors();
      }
    }
  }

  private static byte[] buildDecodingArray() {
    byte[] decoding = new byte[ASCII_CHARACTERS];
    Arrays.fill(decoding, (byte) -1);
    for (int i = 0; i < ALPHABET.length(); i++) {
      char c = ALPHABET.charAt(i);
      decoding[c] = (byte) i;
    }
    return decoding;
  }

  /**
   * Returns the {@code long} value whose base16 representation is stored in the first
   * 16 chars of {@code chars} starting from the {@code offset}.
   * @param chars the base16 representation of the {@code long}.
   */
  private static long longFromBase16String(CharSequence chars) {
    int offset = 0;
    return (decodeByte(chars.charAt(offset), chars.charAt(offset + 1)) & 0xFFL) << 56
            | (decodeByte(chars.charAt(offset + 2), chars.charAt(offset + 3)) & 0xFFL) << 48
            | (decodeByte(chars.charAt(offset + 4), chars.charAt(offset + 5)) & 0xFFL) << 40
            | (decodeByte(chars.charAt(offset + 6), chars.charAt(offset + 7)) & 0xFFL) << 32
            | (decodeByte(chars.charAt(offset + 8), chars.charAt(offset + 9)) & 0xFFL) << 24
            | (decodeByte(chars.charAt(offset + 10), chars.charAt(offset + 11)) & 0xFFL) << 16
            | (decodeByte(chars.charAt(offset + 12), chars.charAt(offset + 13)) & 0xFFL) << 8
            | (decodeByte(chars.charAt(offset + 14), chars.charAt(offset + 15)) & 0xFFL);
  }

  private static byte decodeByte(char hi, char lo) {
    int decoded = DECODING[hi] << 4 | DECODING[lo];
    return (byte) decoded;
  }

  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L397-L402

  static List<SpanLog> convertAnnotationsToSpanLogs(FinishedSpan span) {
    return span.getEvents().stream()
            .map(entry -> new SpanLog(entry.getKey(), Collections.singletonMap("annotation", entry.getValue())))
            .collect(Collectors.toList());
  }

  @Override
  public void run() {
    while (!stop.get()) {
      try {
        SpanToSend spanToSend = spanBuffer.take();
        if (spanToSend == DeathPill.INSTANCE) {
          LOG.info("reporting thread stopping");
          return;
        }
        send(spanToSend.getTraceContext(), spanToSend.getFinishedSpan());
      } catch (InterruptedException ex) {
        if (LOG.isInfoEnabled()) {
          LOG.info("reporting thread interrupted");
        }
      } catch (Throwable ex) {
        LOG.warn("Error processing buffer", ex);
        spanMetrics.reportErrors();
      }
    }
  }

  @Override
  public void close() {
    if (!stop.compareAndSet(false, true)) {
      // Ignore multiple stop calls
      return;
    }

    try {
      // This will release the thread if it's waiting in BlockingQueue#take()
      spanBuffer.offer(DeathPill.INSTANCE);
      // wait for 5 secs max to send remaining spans
      sendingThread.join(5000);
      sendingThread.interrupt();
      heartbeatMetricsScheduledExecutorService.shutdownNow();
    }
    catch (InterruptedException ex) {
      // no-op
    }

    try {
      // It seems WavefrontClient does not support graceful shutdown, so we need to
      // flush manually, and
      // send should not be called after flush
      wavefrontSender.flush();
      wavefrontSender.close();
    }
    catch (IOException e) {
      LOG.warn("Unable to close Wavefront Client", e);
    }
  }

  // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/WavefrontTracer.java#L275-L280

  static List<Pair<String, String>> createDefaultTags(ApplicationTags applicationTags) {
    List<Pair<String, String>> result = new ArrayList<>();
    result.add(Pair.of(APPLICATION_TAG_KEY, applicationTags.getApplication()));
    result.add(Pair.of(SERVICE_TAG_KEY, applicationTags.getService()));
    result.add(Pair.of(CLUSTER_TAG_KEY,
        applicationTags.getCluster() == null ? NULL_TAG_VAL : applicationTags.getCluster()));
    result.add(
        Pair.of(SHARD_TAG_KEY, applicationTags.getShard() == null ? NULL_TAG_VAL : applicationTags.getShard()));
    if (applicationTags.getCustomTags() != null) {
      applicationTags.getCustomTags().forEach((k, v) -> result.add(Pair.of(k, v)));
    }
    return result;
  }
  private static class SpanToSend {

    private final TraceContext traceContext;

    private final FinishedSpan finishedSpan;

    SpanToSend(TraceContext traceContext, FinishedSpan finishedSpan) {
      this.traceContext = traceContext;
      this.finishedSpan = finishedSpan;
    }

    TraceContext getTraceContext() {
      return traceContext;
    }

    FinishedSpan getFinishedSpan() {
      return finishedSpan;
    }

  }

  /**
   * Gets queued into {@link #spanBuffer} if {@link #close()} is called and will lead
   * the sender thread to stop.
   */
  private static class DeathPill extends SpanToSend {

    static final DeathPill INSTANCE = new DeathPill();

    private DeathPill() {
      super(null, null);
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
        FinishedSpan span
    ){
      super(defaultTags.size() + span.getTags().size());
      // TODO: OTel doesn't have a notion of debug
      boolean debug = false;
      boolean hasAnnotations = span.getEvents().size() > 0;
      isError = span.getError() != null;

      addAll(defaultTags);
      for (Map.Entry<String, String> tag : span.getTags().entrySet()) {
        String lowerCaseKey = tag.getKey().toLowerCase(Locale.ROOT);
        if (lowerCaseKey.equals(ERROR_TAG_KEY)) {
          isError = true;
          continue; // We later replace whatever the potentially empty value was with "true"
        }
        if (tag.getValue().isEmpty()) continue;
        if (defaultTagKeys.contains(lowerCaseKey)) continue;
        if (lowerCaseKey.equals(DEBUG_TAG_KEY)) {
          debug = true; // This tag is set out-of-band
          continue;
        }
        if (lowerCaseKey.equals(COMPONENT_TAG_KEY)) {
          componentTagValue = tag.getValue();
        }
        add(Pair.of(tag.getKey(), tag.getValue()));
      }

      // Check for span.error() for uncaught exception in request mapping and add it to Wavefront span tag
      if (isError) add(Pair.of("error", "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L300-L303
      if (debug) add(Pair.of(DEBUG_TAG_KEY, "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L254-L266
      if (span.getKind() != null) {
        String kind = span.getKind().toString().toLowerCase();
        add(Pair.of("span.kind", kind));
        if (hasAnnotations) {
          add(Pair.of("_spanSecondaryId", kind));
        }
      }

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L329-L332
      if (hasAnnotations) add(Pair.of(SPAN_LOG_KEY, "true"));

      // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L324-L327
      if (span.getLocalIp() != null) {
        String localIp = span.getLocalIp();
        String version = localIp.contains(":") ? "ipv6" : "ipv4";
        add(Pair.of(version, localIp));
      }
    }
  }
}
