package com.wavefront.spring.actuate;

import java.net.URI;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountInfo;
import com.wavefront.spring.account.AccountManagementClient;
import com.wavefront.spring.autoconfigure.WavefrontProperties;
import io.micrometer.wavefront.WavefrontConfig;
import org.junit.jupiter.api.Test;

import org.springframework.boot.actuate.cache.CachesEndpoint;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.web.client.RestTemplateAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link WavefrontEndpointAutoConfiguration}.
 *
 * @author Stephane Nicoll
 */
class WavefrontEndpointAutoConfigurationTests {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(RestTemplateAutoConfiguration.class,
          WavefrontEndpointAutoConfiguration.class));

  @Test
  void runShouldHaveEndpointBean() {
    this.contextRunner.withUserConfiguration(AccountManagementConfiguration.class)
        .withPropertyValues("management.endpoints.web.exposure.include=wavefront")
        .run((context) -> assertThat(context).hasSingleBean(WavefrontController.class));
  }

  @Test
  void runWithoutWavefrontConfigShouldNotHaveEndpointBean() {
    this.contextRunner.withBean(ApplicationTags.class, () -> mock(ApplicationTags.class))
        .withPropertyValues("management.endpoints.web.exposure.include=wavefront")
        .run((context) -> assertThat(context).doesNotHaveBean(WavefrontController.class));
  }

  @Test
  void runWithoutApplicationTagsShouldNotHaveEndpointBean() {
    this.contextRunner.withBean(WavefrontConfig.class, () -> mock(WavefrontConfig.class))
        .withPropertyValues("management.endpoints.web.exposure.include=wavefront")
        .run((context) -> assertThat(context).doesNotHaveBean(WavefrontController.class));
  }


  @Test
  void runWhenNotExposedShouldNotHaveEndpointBean() {
    this.contextRunner.withBean(WavefrontConfig.class, () -> mock(WavefrontConfig.class))
        .withBean(ApplicationTags.class, () -> mock(ApplicationTags.class))
        .run((context) -> assertThat(context).doesNotHaveBean(CachesEndpoint.class));
  }

  @Test
  void runWhenEnabledPropertyIsFalseShouldNotHaveEndpointBean() {
    this.contextRunner.withBean(WavefrontConfig.class, () -> mock(WavefrontConfig.class))
        .withBean(ApplicationTags.class, () -> mock(ApplicationTags.class))
        .withPropertyValues("management.endpoint.wavefront.enabled:false")
        .withPropertyValues("management.endpoints.web.exposure.include=*")
        .run((context) -> assertThat(context).doesNotHaveBean(CachesEndpoint.class));
  }

  @Test
  void runWithFremiumAccount() {
    this.contextRunner.withUserConfiguration(AccountManagementConfiguration.class)
        .withPropertyValues("wavefront.freemium-account=true",
            "management.endpoints.web.exposure.include=wavefront")
        .run((context) -> {
          assertThat(context).hasSingleBean(WavefrontController.class);
          assertThat(context.getBean(WavefrontController.class).dashboard().getHeaders()
              .getLocation()).isEqualTo(URI.create("https://example.com/go"));
        });
  }

  @Test
  void runWithNonFreemiumAccount() {
    this.contextRunner.withUserConfiguration(AccountManagementConfiguration.class)
        .withPropertyValues("management.endpoints.web.exposure.include=wavefront")
        .run((context) -> {
          assertThat(context).hasSingleBean(WavefrontController.class);
          assertThat(context.getBean(WavefrontController.class).dashboard().getHeaders()
              .getLocation()).isEqualTo(URI.create("https://example.com"));
        });
  }

  @Configuration(proxyBeanMethods = false)
  @EnableConfigurationProperties(WavefrontProperties.class)
  static class AccountManagementConfiguration {

    private final ApplicationTags applicationTags = mock(ApplicationTags.class);

    @Bean
    public ApplicationTags applicationTags() {
      return this.applicationTags;
    }

    @Bean
    AccountManagementClient accountManagementClient() {
      AccountManagementClient client = mock(AccountManagementClient.class);
      given(client.getExistingAccount("https://example.com", this.applicationTags, "abc-123"))
          .willReturn(new AccountInfo("abc-123", "https://example.com/go"));
      return client;
    }

    @Bean
    WavefrontConfig wavefrontConfig() {
      return testWavefrontConfig("https://example.com", "abc-123");
    }

  }

  private static WavefrontConfig testWavefrontConfig(String uri, String apiToken) {
    return new WavefrontConfig() {
      @Override
      public String get(String key) {
        return null;
      }

      @Override
      public String uri() {
        return uri;
      }

      @Override
      public String apiToken() {
        return apiToken;
      }
    };
  }

}
