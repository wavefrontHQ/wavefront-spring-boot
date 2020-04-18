package com.wavefront.spring.autoconfigure;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import brave.handler.FinishedSpanHandler;
import brave.handler.MutableSpan;
import brave.handler.MutableSpan.AnnotationConsumer;
import brave.handler.MutableSpan.TagConsumer;
import brave.propagation.TraceContext;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.entities.tracing.SpanLog;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import static com.wavefront.sdk.common.Constants.DEBUG_TAG_KEY;
import static com.wavefront.sdk.common.Constants.ERROR_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SOURCE_KEY;
import static com.wavefront.sdk.common.Constants.SPAN_LOG_KEY;

/**
 * This converts a span recorded by Brave and invokes {@link WavefrontTracingSpanSender#sendSpan}.
 *
 * <p>This uses a combination of conversion approaches from Wavefront projects:
 * <ul>
 *   <li>https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java</li>
 *   <li>https://github.com/wavefrontHQ/wavefront-proxy</li>
 * </ul>
 *
 * <p>On conflict, we make a comment and prefer wavefront-opentracing-sdk-java. The rationale is
 * wavefront-opentracing-sdk-java uses the same {@link WavefrontTracingSpanSender#sendSpan} library,
 * so it is easier to reason with. This policy can be revisited by future maintainers.
 *
 * <p><em>Note:</em>UUID conversions follow the same conventions used in practice in Wavefront.
 * Ex. https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/6babf2ff95daa37452e1e8c35ae54b58b6abb50f/src/main/java/com/wavefront/opentracing/propagation/JaegerWavefrontPropagator.java#L191-L204
 * While in practice this is not a problem, it is worth mentioning that this convention will only
 * only result in RFC 4122 timestamp (version 1) format by accident. In other words, don't call
 * {@link UUID#timestamp()} on UUIDs converted here, or in other Wavefront code, as it might
 * throw.
 */
final class WavefrontSpanHandler extends FinishedSpanHandler {
  private static final Log LOG = LogFactory.getLog(WavefrontSpanHandler.class);
  // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L113-L114
  private static final String DEFAULT_SPAN_NAME = "defaultOperation";

  final WavefrontTracingSpanSender wavefrontSender;
  final String source;
  final List<Pair<String, String>> defaultTags;
  final Set<String> defaultTagKeys;

  WavefrontSpanHandler(WavefrontTracingSpanSender wavefrontSender, String source,
      List<Pair<String, String>> defaultTags) {
    this.wavefrontSender = wavefrontSender;
    this.source = source;
    this.defaultTags = defaultTags;
    this.defaultTagKeys = defaultTags.stream().map(p -> p._1).collect(Collectors.toSet());
    this.defaultTagKeys.add(SOURCE_KEY);
  }

  @Override public boolean handle(TraceContext context, MutableSpan span) {
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
    long start = span.startTimestamp() / 1000L, finish = span.finishTimestamp() / 1000L;
    long duration = start != 0 && finish != 0L ? Math.max(finish - start, 1L) : 0L;

    WavefrontConsumer wavefrontConsumer = new WavefrontConsumer(defaultTagKeys);

    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L397-L402
    List<SpanLog> spanLogs = new ArrayList<>();
    span.forEachAnnotation(wavefrontConsumer, spanLogs);

    List<Pair<String, String>> tags = new ArrayList<>(defaultTags);
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
      wavefrontSender.sendSpan(name, start, duration, source, traceId, spanId,
          parents, followsFrom, tags, spanLogs);
    } catch (IOException | RuntimeException t) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("error sending span " + context, t);
      }
    }
    return true; // regardless of error, other handlers should run
  }

  static class WavefrontConsumer
      implements AnnotationConsumer<List<SpanLog>>, TagConsumer<List<Pair<String, String>>> {
    final Set<String> defaultTagKeys;
    boolean debug, hasAnnotations;

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
      }
      target.add(Pair.of(key, value));
    }
  }
}