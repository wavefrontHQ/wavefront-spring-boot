package com.wavefront.spring.actuate;

import java.net.URI;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.account.AccountInfo;
import com.wavefront.spring.account.AccountManagementClient;
import io.micrometer.wavefront.WavefrontConfig;

/**
 * Provide a one time link to the dashboard of an account that was auto-negotiated.
 *
 * @author Stephane Nicoll
 */
class OneTimeDashboardUrlSupplier implements Supplier<URI> {

  private final AccountManagementClient accountManagementClient;

  private final WavefrontConfig wavefrontConfig;

  private final ApplicationTags applicationTags;

  OneTimeDashboardUrlSupplier(AccountManagementClient accountManagementClient,
      WavefrontConfig wavefrontConfig, ApplicationTags applicationTags) {
    this.accountManagementClient = accountManagementClient;
    this.wavefrontConfig = wavefrontConfig;
    this.applicationTags = applicationTags;
  }

  @Override
  public URI get() {
    AccountInfo account = this.accountManagementClient.getExistingAccount(
        this.wavefrontConfig.uri(), this.applicationTags, this.wavefrontConfig.apiToken());
    return URI.create(account.getLoginUrl());
  }

}
