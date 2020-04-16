package com.wavefront.spring.autoconfigure.account;

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Information of a provisioned account.
 *
 * @author Stephane Nicoll
 */
class AccountInfo {

  private final String apiToken;

  private final String loginUri;

  AccountInfo(String apiToken, String loginUri) {
    this.apiToken = apiToken;
    this.loginUri = loginUri;
  }

  /**
   * Return the api token of the account.
   * @return the api token
   */
  String getApiToken() {
    return this.apiToken;
  }

  /**
   * Determine the one time login url based on the specified Wavefront cluster.
   * @param clusterUri the uri of the cluster
   * @return the one time login url for that account
   */
  String determineLoginUrl(String clusterUri) {
    return UriComponentsBuilder.fromUriString(clusterUri).path(this.loginUri).build().toUriString();
  }

}
