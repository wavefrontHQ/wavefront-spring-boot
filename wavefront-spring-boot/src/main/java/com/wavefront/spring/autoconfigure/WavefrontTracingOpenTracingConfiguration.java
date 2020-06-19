package com.wavefront.spring.autoconfigure;

import java.util.HashSet;

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A fallback configuration for OpenTracing if Spring Cloud Sleuth is not available.
 *
 * @author Stephane Nicoll
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ Reporter.class, Tracer.class })
@ConditionalOnMissingBean(name = WavefrontTracingSleuthConfiguration.BEAN_NAME)
class WavefrontTracingOpenTracingConfiguration {

  @Bean(destroyMethod = "flush")
  @ConditionalOnMissingBean(Tracer.class)
  @ConditionalOnBean(WavefrontSender.class)
  WavefrontTracer wavefrontTracer(WavefrontSender wavefrontSender, ApplicationTags applicationTags,
      WavefrontConfig wavefrontConfig, WavefrontProperties wavefrontProperties) {
    Reporter spanReporter = new WavefrontSpanReporter.Builder().withSource(wavefrontConfig.source())
        .build(wavefrontSender);
    WavefrontTracer.Builder builder = new WavefrontTracer.Builder(spanReporter, applicationTags)
        .excludeJvmMetrics();  // reported separately
    builder.redMetricsCustomTagKeys(
        new HashSet<>(wavefrontProperties.getTracing().getRedMetricsCustomTagKeys()));
    return builder.build();
  }

}
