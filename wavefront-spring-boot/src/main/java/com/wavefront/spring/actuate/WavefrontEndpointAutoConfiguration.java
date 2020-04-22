package com.wavefront.spring.actuate;

import java.net.URI;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountManagementClient;
import com.wavefront.spring.autoconfigure.WavefrontAutoConfiguration;
import io.micrometer.wavefront.WavefrontConfig;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.web.client.RestTemplate;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for {@link WavefrontController}.
 *
 * @author Stephane Nicoll
 */
@ConditionalOnClass({ RestTemplate.class, WavefrontConfig.class, ApplicationTags.class })
@ConditionalOnBean({ RestTemplateBuilder.class, WavefrontConfig.class, ApplicationTags.class })
@ConditionalOnAvailableEndpoint(endpoint = WavefrontController.class)
@AutoConfigureAfter({ WavefrontAutoConfiguration.class, RestTemplateAutoConfiguration.class })
public class WavefrontEndpointAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AccountManagementClient accountManagementClient(RestTemplateBuilder restTemplateBuilder) {
    return new AccountManagementClient(restTemplateBuilder);
  }

  @Bean
  @ConditionalOnMissingBean
  WavefrontController wavefrontController(Environment environment,
      AccountManagementClient accountManagementClient, WavefrontConfig wavefrontConfig,
      ApplicationTags applicationTags) {
    Boolean managedAccount = environment.getProperty("wavefront.managed-account", Boolean.class, false);
    if (managedAccount) {
      return new WavefrontController(new OneTimeDashboardUrlSupplier(
          accountManagementClient, wavefrontConfig, applicationTags));
    }
    return new WavefrontController(() -> URI.create(wavefrontConfig.uri()));
  }

}
