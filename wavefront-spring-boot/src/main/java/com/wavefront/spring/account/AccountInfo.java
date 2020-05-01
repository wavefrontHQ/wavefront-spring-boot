package com.wavefront.spring.account;

/**
 * Information of a provisioned account.
 *
 * @author Stephane Nicoll
 */
public final class AccountInfo {

  private final String apiToken;

  private final String loginUrl;

  public AccountInfo(String apiToken, String loginUrl) {
    this.apiToken = apiToken;
    this.loginUrl = loginUrl;
  }

  /**
   * Return the api token of the account.
   * @return the api token
   */
  public String getApiToken() {
    return this.apiToken;
  }

  /**
   * Return the one time login url for that account.
   * @return the one time login url for that account
   */
  public String getLoginUrl() {
    return this.loginUrl;
  }

}
