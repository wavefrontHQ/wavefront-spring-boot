package com.wavefront.spring.autoconfigure;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.ApplicationTags.Builder;

import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Factory that can be used to create an {@link ApplicationTags}.
 *
 * @author Stephane Nicoll
 */
public class ApplicationTagsFactory {

  private static final String DEFAULT_APPLICATION_NAME = "unnamed_application";
  private static final String DEFAULT_SERVICE_NAME = "unnamed_service";
  private static final String PREFIX = "management.wavefront.application.";

  private final List<ApplicationTagsBuilderCustomizer> customizers;

  /**
   * Create an instance with the specified {@link ApplicationTagsBuilderCustomizer
   * customizers}.
   * @param customizers the customizers (can be {@code null}).
   */
  public ApplicationTagsFactory(List<ApplicationTagsBuilderCustomizer> customizers) {
    this.customizers = (customizers != null) ? customizers : Collections.emptyList();
  }

  /**
   * Create an instance with no further customization.
   */
  public ApplicationTagsFactory() {
    this(null);
  }

  /**
   * Create an {@link ApplicationTags} from the {@link Environment}.
   * @param environment the environment
   * @return a matching {@link ApplicationTags}
   */
  public ApplicationTags createFromEnvironment(Environment environment) {
    String name = getValue(environment, "name", () -> DEFAULT_APPLICATION_NAME);
    String service = getValue(environment, "service-name", () -> defaultServiceName(environment));
    return customize(new Builder(name, service).cluster(getValue(environment, "cluster-name", () -> null))
        .shard(getValue(environment, "shard-name", () -> null))).build();
  }

  private String defaultServiceName(Environment environment) {
    String applicationName = environment.getProperty("spring.application.name");
    return (StringUtils.hasText(applicationName)) ? applicationName : DEFAULT_SERVICE_NAME;
  }

  private Builder customize(Builder builder) {
    this.customizers.forEach((customizer) -> customizer.customize(builder));
    return builder;
  }

  private String getValue(Environment environment, String name, Supplier<String> fallback) {
    String value = environment.getProperty(PREFIX + name);
    return (value != null) ? value : fallback.get();
  }

}
