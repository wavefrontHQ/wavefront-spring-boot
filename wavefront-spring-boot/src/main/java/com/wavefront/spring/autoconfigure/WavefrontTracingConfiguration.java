package com.wavefront.spring.autoconfigure;

import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.wavefront.WavefrontConfig;
import io.opentracing.Tracer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for Wavefront tracing.
 *
 * @author Stephane Nicoll
 */
@ConditionalOnClass({ Reporter.class, Tracer.class })
@ConditionalOnProperty(value = "wavefront.tracing.enabled", matchIfMissing = true)
class WavefrontTracingConfiguration {

  @Bean
  @ConditionalOnMissingBean(Tracer.class)
  @ConditionalOnBean(WavefrontSender.class)
  WavefrontTracer wavefrontTracer(WavefrontSender wavefrontSender, ApplicationTags applicationTags,
      WavefrontConfig wavefrontConfig) {
    Reporter spanReporter = new WavefrontSpanReporter.Builder().withSource(wavefrontConfig.source())
        .build(wavefrontSender);
    return new WavefrontTracer.Builder(spanReporter, applicationTags).build();
  }

}
