package com.wavefront.spring.actuate;

import java.net.URI;

import com.wavefront.sdk.common.Utils;
import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountManagementClient;

import io.micrometer.wavefront.WavefrontConfig;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.tracing.wavefront.WavefrontTracingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

import static com.wavefront.spring.autoconfigure.AccountManagementEnvironmentPostProcessor.FREEMIUM_ACCOUNT_PROPERTY;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link WavefrontController}.
 *
 * @author Stephane Nicoll
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnClass({ RestTemplate.class, WavefrontConfig.class, ApplicationTags.class })
@ConditionalOnBean({ RestTemplateBuilder.class, WavefrontConfig.class, ApplicationTags.class })
@ConditionalOnAvailableEndpoint(endpoint = WavefrontController.class)
@AutoConfigureAfter({ WavefrontMetricsExportAutoConfiguration.class, WavefrontTracingAutoConfiguration.class, RestTemplateAutoConfiguration.class })
public class WavefrontEndpointAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AccountManagementClient accountManagementClient(RestTemplateBuilder restTemplateBuilder) {
    return new AccountManagementClient(restTemplateBuilder,
        Utils.getVersion("wavefront-spring-boot").orElse(null));
  }

  @Bean
  @ConditionalOnMissingBean
  WavefrontController wavefrontController(Environment environment,
      AccountManagementClient accountManagementClient, WavefrontConfig wavefrontConfig,
      ApplicationTags applicationTags) {
    if (isFreemium(environment)) {
      return new WavefrontController(new OneTimeDashboardUrlSupplier(
          accountManagementClient, wavefrontConfig, applicationTags));
    }
    return new WavefrontController(() -> URI.create(wavefrontConfig.uri()));
  }

  private static boolean isFreemium(Environment environment) {
    return Boolean.TRUE.equals(environment.getProperty(FREEMIUM_ACCOUNT_PROPERTY, Boolean.class, Boolean.FALSE));
  }

}
