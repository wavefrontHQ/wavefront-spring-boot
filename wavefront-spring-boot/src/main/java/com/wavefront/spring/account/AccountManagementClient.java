package com.wavefront.spring.account;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.wavefront.sdk.common.application.ApplicationTags;

import org.springframework.boot.json.BasicJsonParser;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Manage a Wavefront {@linkplain AccountInfo account} based on an
 * {@link ApplicationTags}.
 *
 * @author Stephane Nicoll
 */
public class AccountManagementClient {

  private final RestTemplate restTemplate;

  private final String version;

  /**
   * Create an instance using the specified {@link RestTemplateBuilder} and starter
   * {@code version}.
   * @param restTemplateBuilder the builder to use to configure the {@link RestTemplate}.
   * @param version the version of the starter or {@code null} if the version could not
   * be determined.
   */
  public AccountManagementClient(RestTemplateBuilder restTemplateBuilder, String version) {
    this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10))
        .setReadTimeout(Duration.ofSeconds(10)).build();
    this.version = version;
  }

  /**
   * Provision an account for the specified Wavefront cluster and application
   * information.
   * @param clusterUri the URI of the Wavefront cluster
   * @param applicationTags the {@link ApplicationTags} to use
   * @return the provisioned account
   * @throws AccountManagementFailedException if the cluster does not support freemium
   * accounts
   */
  public AccountInfo provisionAccount(String clusterUri, ApplicationTags applicationTags) {
    URI requestUri = accountManagementUri(clusterUri, applicationTags);
    try {
      String json = this.restTemplate.postForObject(requestUri, null, String.class);
      Map<String, Object> content = new BasicJsonParser().parseMap(json);
      return new AccountInfo((String) content.get("token"),
          determineLoginUrl(clusterUri, (String) content.get("url")));
    } catch (HttpClientErrorException ex) {
      throw new AccountManagementFailedException(ex.getResponseBodyAsString());
    }
  }

  /**
   * Retrieve an existing account for the specified Wavefront cluster, application
   * information and api token.
   * @param clusterUri the URI of the Wavefront cluster
   * @param applicationTags the {@link ApplicationTags} to use
   * @param apiToken the api token to use
   * @return an existing account information
   * @throws AccountManagementFailedException if the cluster does not support freemium
   * accounts
   */
  public AccountInfo getExistingAccount(String clusterUri, ApplicationTags applicationTags, String apiToken) {
    URI requestUri = accountManagementUri(clusterUri, applicationTags);
    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
    try {
      String json = this.restTemplate
          .exchange(requestUri, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
      Map<String, Object> content = new BasicJsonParser().parseMap(json);
      return new AccountInfo(apiToken, determineLoginUrl(clusterUri, (String) content.get("url")));
    } catch (HttpClientErrorException ex) {
      throw new AccountManagementFailedException(ex.getResponseBodyAsString());
    }
  }

  private URI accountManagementUri(String clusterUri, ApplicationTags applicationTags) {
    UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(clusterUri)
        .path("/api/v2/trial/spring-boot-autoconfigure")
        .queryParam("application", applicationTags.getApplication())
        .queryParam("service", applicationTags.getService());
    if (applicationTags.getCluster() != null) {
      uriComponentsBuilder.queryParam("cluster", applicationTags.getCluster());
    }
    if (applicationTags.getShard() != null) {
      uriComponentsBuilder.queryParam("shard", applicationTags.getShard());
    }
    if (this.version != null) {
      uriComponentsBuilder.queryParam("starterVersion", this.version);
    }
    return uriComponentsBuilder.build().toUri();
  }

  private String determineLoginUrl(String clusterUri, String loginUri) {
    return UriComponentsBuilder.fromUriString(clusterUri).path(loginUri).build().toUriString();
  }

}
