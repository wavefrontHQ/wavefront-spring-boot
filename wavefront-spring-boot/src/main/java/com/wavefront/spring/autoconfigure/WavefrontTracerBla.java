package com.wavefront.spring.autoconfigure;

import com.wavefront.opentracing.WavefrontTracer;

/**
 * Marker interface - once registered as a bean will not apply the default
 * tracing configuration.
 *
 * @author Marcin Grzejszczak
 * @since 2.1.0
 */
public interface WavefrontTracerBla {

}
