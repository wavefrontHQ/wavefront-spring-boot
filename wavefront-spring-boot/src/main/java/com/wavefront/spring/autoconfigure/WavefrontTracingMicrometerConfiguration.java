package com.wavefront.spring.autoconfigure;

import brave.Tracer;
import brave.TracingCustomizer;
import brave.handler.SpanHandler;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.SpanNamer;
import io.micrometer.wavefront.WavefrontConfig;

import org.springframework.boot.actuate.autoconfigure.tracing.BraveAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Wavefront tracing using Micrometer Tracing.
 *
 * @author Adrian Cole
 * @author Stephane Nicoll
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ SpanNamer.class, MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class })
@AutoConfigureBefore(BraveAutoConfiguration.class)
class WavefrontTracingMicrometerConfiguration {

  static final String BEAN_NAME = "wavefrontTracingBraveCustomizer";

  @Bean
  @ConditionalOnBean({ MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class })
  WavefrontSpanHandler wavefrontMicrometerSpanHandler(MeterRegistry meterRegistry,
                                                      WavefrontSender wavefrontSender,
                                                      ApplicationTags applicationTags,
                                                      WavefrontConfig wavefrontConfig,
                                                      WavefrontProperties wavefrontProperties) {
    return new WavefrontSpanHandler(
            // https://github.com/wavefrontHQ/wavefront-opentracing-sdk-java/blob/f1f08d8daf7b692b9b61dcd5bc24ca6befa8e710/src/main/java/com/wavefront/opentracing/reporting/WavefrontSpanReporter.java#L54
            50000, // TODO: maxQueueSize should be a property, ya?
            wavefrontSender,
            meterRegistry,
            wavefrontConfig.source(),
            applicationTags,
            wavefrontProperties);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass({Tracer.class, TracingCustomizer.class, SpanHandler.class })
  static class BraveCustomizerConfiguration {
    @Bean(BEAN_NAME)
    @ConditionalOnMissingBean(name = BEAN_NAME)
    @ConditionalOnBean({ MeterRegistry.class, WavefrontConfig.class, WavefrontSender.class })
    TracingCustomizer wavefrontTracingBraveCustomizer(WavefrontSpanHandler spanHandler) {
      return t -> t.traceId128Bit(true).supportsJoin(false).addSpanHandler(new WavefrontBraveSpanHandler(spanHandler));
    }
  }
}
