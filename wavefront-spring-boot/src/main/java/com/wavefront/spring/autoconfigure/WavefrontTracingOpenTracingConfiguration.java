package com.wavefront.spring.autoconfigure;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.CompositeReporter;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.entities.tracing.sampling.DurationSampler;
import com.wavefront.sdk.entities.tracing.sampling.RateSampler;
import com.wavefront.sdk.entities.tracing.sampling.Sampler;
import io.micrometer.wavefront.WavefrontConfig;
import io.opentracing.Tracer;

import org.springframework.beans.factory.ObjectProvider;
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

  @Bean
  @ConditionalOnMissingBean(Reporter.class)
  @ConditionalOnBean(WavefrontSender.class)
  Reporter wavefrontSpanReporter(WavefrontSender wavefrontSender, WavefrontConfig wavefrontConfig) {
    return new WavefrontSpanReporter.Builder().withSource(wavefrontConfig.source())
        .build(wavefrontSender);
  }

  @Bean(destroyMethod = "flush")
  @ConditionalOnMissingBean(Tracer.class)
  @ConditionalOnBean(Reporter.class)
  WavefrontTracer wavefrontTracer(ApplicationTags applicationTags,
      WavefrontProperties wavefrontProperties, ObjectProvider<Reporter> reporters,
      ObjectProvider<WavefrontTracerBuilderCustomizer> customizers) {
    Reporter[] reporterArray = reporters.orderedStream().toArray(Reporter[]::new);
    Reporter compositeReporter;
    if (reporterArray.length > 1) {
      compositeReporter = new CompositeReporter(reporterArray);
    } else {
      compositeReporter = reporterArray[0];
    }
    WavefrontTracer.Builder builder = new WavefrontTracer.Builder(compositeReporter, applicationTags)
        .excludeJvmMetrics();  // reported separately
    builder.redMetricsCustomTagKeys(
        new HashSet<>(wavefrontProperties.getTracing().getRedMetricsCustomTagKeys()));
    List<Sampler> samplers =
        createSamplers(wavefrontProperties.getTracing().getOpentracing().getSampler());
    samplers.forEach(builder::withSampler);
    customizers.orderedStream().forEach((customizer) -> customizer.customize(builder));
    return builder.build();
  }

  private List<Sampler> createSamplers(WavefrontProperties.Tracing.Opentracing.Sampler samplerProperties) {
    Double probability = samplerProperties.getProbability();
    Duration duration = samplerProperties.getDuration();
    List<Sampler> samplers = new ArrayList<>();
    if (probability != null) {
      samplers.add(new RateSampler(probability));
    }
    if (duration != null) {
      samplers.add(new DurationSampler(duration.toMillis()));
    }
    return samplers;
  }

}
