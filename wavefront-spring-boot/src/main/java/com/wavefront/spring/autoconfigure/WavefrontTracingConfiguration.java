package com.wavefront.spring.autoconfigure;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import brave.TracingCustomizer;
import com.wavefront.sdk.common.Pair;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.WavefrontTracingSpanSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.wavefront.WavefrontConfig;

import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.sleuth.LocalServiceName;
import org.springframework.cloud.sleuth.autoconfig.TraceAutoConfiguration;
import org.springframework.cloud.sleuth.sampler.SamplerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static com.wavefront.sdk.common.Constants.APPLICATION_TAG_KEY;
import static com.wavefront.sdk.common.Constants.CLUSTER_TAG_KEY;
import static com.wavefront.sdk.common.Constants.NULL_TAG_VAL;
import static com.wavefront.sdk.common.Constants.SERVICE_TAG_KEY;
import static com.wavefront.sdk.common.Constants.SHARD_TAG_KEY;

/**
 * Configuration for Wavefront tracing.
 *
 * @author Stephane Nicoll
 */
@ConditionalOnClass(TracingCustomizer.class)
@AutoConfigureBefore(TraceAutoConfiguration.class)
// This import is safe, but can be removed in Sleuth 2.2.3 or 3.0.0.M2
@Import(SamplerAutoConfiguration.class)
@ConditionalOnProperty(value = "wavefront.tracing.enabled", matchIfMissing = true)
class WavefrontTracingConfiguration {
  static final String BEAN_NAME = "wavefrontTracingCustomizer";
  /**
   * Wavefront use a combination of null and non-values in defaults. Some non-values are not defined
   * by constants. This constant helps reduce drift in non-value comparison.
   */
  static final String DEFAULT_SERVICE = new WavefrontProperties().getApplication().getService();

  @Bean(BEAN_NAME)
  @ConditionalOnMissingBean(name = BEAN_NAME)
  @ConditionalOnBean(WavefrontTracingSpanSender.class)
  TracingCustomizer wavefrontTracingCustomizer(
      MeterRegistry meterRegistry,
      WavefrontTracingSpanSender wavefrontSender,
      ApplicationTags applicationTags,
      WavefrontConfig wavefrontConfig,
      @LocalServiceName String serviceName
  ) {
    WavefrontSpanHandler spanHandler = new WavefrontSpanHandler(
        // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L54
        50000, // TODO: maxQueueSize should be a property, ya?
        wavefrontSender,
        meterRegistry,
        wavefrontConfig.source(),
        createDefaultTags(applicationTags, serviceName)
    );

    return t -> t.traceId128Bit(true).supportsJoin(false).addFinishedSpanHandler(spanHandler);
  }

  // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/WavefrontTracer.java#L275-L280
  static List<Pair<String, String>> createDefaultTags(ApplicationTags applicationTags,
      String serviceName) {
    List<Pair<String, String>> result = new ArrayList<>();
    result.add(Pair.of(APPLICATION_TAG_KEY, applicationTags.getApplication()));
    // Prefer the user's service name unless they overwrote it with the wavefront property
    // https://github.com/wavefrontHQ/wavefront-proxy/blob/3dd1fa11711a04de2d9d418e2269f0f9fb464f36/proxy/src/main/java/com/wavefront/agent/listeners/tracing/ZipkinPortUnificationHandler.java#L263-L266
    if (!Objects.equals(applicationTags.getService(), DEFAULT_SERVICE)) {
      result.add(Pair.of(SERVICE_TAG_KEY, applicationTags.getService()));
    } else {
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
