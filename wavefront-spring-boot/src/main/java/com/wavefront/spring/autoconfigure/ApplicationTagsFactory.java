package com.wavefront.spring.autoconfigure;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.ApplicationTags.Builder;
import com.wavefront.spring.autoconfigure.WavefrontProperties.Application;

import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

/**
 * Factory that can be used to create an {@link ApplicationTags}.
 *
 * @author Stephane Nicoll
 */
public class ApplicationTagsFactory {

  private static final String PREFIX = "wavefront.application.";

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
   * Create an {@link ApplicationTags} from properties.
   * @param environment the environment to use for fallback values
   * @param properties the wavefront properties
   * @return a matching {@link ApplicationTags}
   */
  public ApplicationTags createFromProperties(Environment environment, WavefrontProperties properties) {
    Application application = properties.getApplication();
    String service = (StringUtils.hasText(application.getService()))
        ? application.getService() : defaultServiceName(environment);
    Builder builder = new Builder(application.getName(), service);
    PropertyMapper mapper = PropertyMapper.get().alwaysApplyingWhenNonNull();
    mapper.from(application::getCluster).to(builder::cluster);
    mapper.from(application::getShard).to(builder::shard);
    return customize(builder).build();
  }

  /**
   * Create an {@link ApplicationTags} from the {@link Environment}.
   * @param environment the environment
   * @return a matching {@link ApplicationTags}
   */
  public ApplicationTags createFromEnvironment(Environment environment) {
    String name = getValue(environment, "name", () -> "unnamed_application");
    String service = getValue(environment, "service", () -> defaultServiceName(environment));
    return customize(new Builder(name, service).cluster(getValue(environment, "cluster", () -> null))
        .shard(getValue(environment, "shard", () -> null))).build();
  }

  private String defaultServiceName(Environment environment) {
    String applicationName = environment.getProperty("spring.application.name");
    return (StringUtils.hasText(applicationName)) ? applicationName : Application.DEFAULT_SERVICE_NAME;
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
