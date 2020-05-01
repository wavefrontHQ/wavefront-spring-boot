package com.wavefront.spring.autoconfigure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Configuration for Wavefront tracing.
 *
 * @author Stephane Nicoll
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(value = "wavefront.tracing.enabled", matchIfMissing = true)
@Import({ WavefrontTracingSleuthConfiguration.class, WavefrontTracingOpenTracingConfiguration.class })
class WavefrontTracingConfiguration {

}
