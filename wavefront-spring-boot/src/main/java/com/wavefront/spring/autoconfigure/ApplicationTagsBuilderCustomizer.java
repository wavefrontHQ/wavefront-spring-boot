package com.wavefront.spring.autoconfigure;

import com.wavefront.sdk.common.application.ApplicationTags;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link ApplicationTags} via a {@link ApplicationTags.Builder} whilst retaining default
 * auto-configuration.
 *
 * @author Stephane Nicoll
 */
@FunctionalInterface
public interface ApplicationTagsBuilderCustomizer {

  /**
   * Customize the {@link ApplicationTags.Builder}.
   * @param builder the builder to customize
   */
  void customize(ApplicationTags.Builder builder);

}
