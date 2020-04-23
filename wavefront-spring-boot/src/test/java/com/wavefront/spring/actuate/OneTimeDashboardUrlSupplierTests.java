package com.wavefront.spring.actuate;

import java.net.URI;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountInfo;
import com.wavefront.spring.account.AccountManagementClient;
import com.wavefront.spring.account.AccountManagementFailedException;
import io.micrometer.wavefront.WavefrontConfig;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link OneTimeDashboardUrlSupplier}.
 *
 * @author Stephane Nicoll
 */
class OneTimeDashboardUrlSupplierTests {

  @Test
  void linkIsRetrievedFromAccount() {
    ApplicationTags applicationTags = mock(ApplicationTags.class);
    WavefrontConfig wavefrontConfig = testWavefrontConfig("https://example.com", "abc-def");
    AccountManagementClient client = mock(AccountManagementClient.class);
    given(client.getExistingAccount("https://example.com", applicationTags, "abc-def"))
        .willReturn(new AccountInfo("abc-def", "https://example.com/123"));
    URI dashboardUrl = new OneTimeDashboardUrlSupplier(client, wavefrontConfig,
        applicationTags).get();
    assertThat(dashboardUrl).isEqualTo(URI.create("https://example.com/123"));
    verify(client).getExistingAccount("https://example.com", applicationTags, "abc-def");
  }

  @Test
  void clusterUriIsUsedIfAccountRetrievalFailed() {
    ApplicationTags applicationTags = mock(ApplicationTags.class);
    WavefrontConfig wavefrontConfig = testWavefrontConfig("https://example.com", "abc-def");
    AccountManagementClient client = mock(AccountManagementClient.class);
    given(client.getExistingAccount("https://example.com", applicationTags, "abc-def"))
        .willThrow(new AccountManagementFailedException("Test Exception"));
    assertThatThrownBy(() -> new OneTimeDashboardUrlSupplier(client, wavefrontConfig,
        applicationTags).get()).hasMessageContaining("Test Exception");
    verify(client).getExistingAccount("https://example.com", applicationTags, "abc-def");
  }

  @Test
  void accountInfoIsRetrievedAtEveryInvocation() {
    ApplicationTags applicationTags = mock(ApplicationTags.class);
    WavefrontConfig wavefrontConfig = testWavefrontConfig("https://example.com", "abc-def");
    AccountManagementClient client = mock(AccountManagementClient.class);
    given(client.getExistingAccount("https://example.com", applicationTags, "abc-def"))
        .willAnswer(new Answer<AccountInfo>() {

          private int count;

          @Override
          public AccountInfo answer(InvocationOnMock invocationOnMock) {
            return new AccountInfo("abc-def", "https://example.com/" + (++count));
          }

        });
    OneTimeDashboardUrlSupplier dashboardUrlSupplier = new OneTimeDashboardUrlSupplier(
        client, wavefrontConfig, applicationTags);
    assertThat(dashboardUrlSupplier.get()).isEqualTo(URI.create("https://example.com/1"));
    assertThat(dashboardUrlSupplier.get()).isEqualTo(URI.create("https://example.com/2"));
    assertThat(dashboardUrlSupplier.get()).isEqualTo(URI.create("https://example.com/3"));
  }

  private WavefrontConfig testWavefrontConfig(String uri, String apiToken) {
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
