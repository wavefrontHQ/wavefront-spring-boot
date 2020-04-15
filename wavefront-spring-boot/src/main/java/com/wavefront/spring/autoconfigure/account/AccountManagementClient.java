/*
 * Copyright 2012-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.wavefront.spring.autoconfigure.account;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

import com.wavefront.sdk.common.application.ApplicationTags;
import org.apache.commons.logging.Log;

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
class AccountManagementClient {

	private final Log logger;

	private final RestTemplate restTemplate;

	AccountManagementClient(Log logger, RestTemplateBuilder restTemplateBuilder) {
		this.logger = logger;
		this.restTemplate = restTemplateBuilder.setConnectTimeout(Duration.ofSeconds(10))
				.setReadTimeout(Duration.ofSeconds(10)).build();
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
	AccountInfo provisionAccount(String clusterUri, ApplicationTags applicationTags) {
		URI requestUri = accountManagementUri(clusterUri, applicationTags);
		this.logger.debug("Auto-negotiating Wavefront user account from " + requestUri);
		try {
			String json = this.restTemplate.postForObject(requestUri, null, String.class);
			Map<String, Object> content = new BasicJsonParser().parseMap(json);
			return new AccountInfo((String) content.get("token"), (String) content.get("url"));
		}
		catch (HttpClientErrorException ex) {
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
	AccountInfo getExistingAccount(String clusterUri, ApplicationTags applicationTags, String apiToken) {
		URI requestUri = accountManagementUri(clusterUri, applicationTags);
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken);
		this.logger.debug("Retrieving existing account from " + requestUri);
		try {
			String json = this.restTemplate
					.exchange(requestUri, HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
			Map<String, Object> content = new BasicJsonParser().parseMap(json);
			return new AccountInfo(apiToken, (String) content.get("url"));
		}
		catch (HttpClientErrorException ex) {
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
		return uriComponentsBuilder.build().toUri();
	}

}
