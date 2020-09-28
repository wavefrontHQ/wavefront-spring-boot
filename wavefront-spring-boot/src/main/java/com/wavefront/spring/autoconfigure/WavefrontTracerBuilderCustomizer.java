package com.wavefront.spring.autoconfigure;

import com.wavefront.opentracing.WavefrontTracer;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link WavefrontTracer} via a {@link WavefrontTracer.Builder} whilst retaining default
 * auto-configuration.
 *
 * @author Han Zhang
 */
@FunctionalInterface
public interface WavefrontTracerBuilderCustomizer {

  /**
   * Customize the {@link WavefrontTracer.Builder}.
   * @param builder the builder to customize
   */
  void customize(WavefrontTracer.Builder builder);

}
