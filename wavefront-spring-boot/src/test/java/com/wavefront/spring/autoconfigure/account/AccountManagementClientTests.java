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

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.spring.autoconfigure.ApplicationTagsFactory;
import org.apache.commons.logging.Log;
import org.junit.jupiter.api.Test;

import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Tests for {@link AccountManagementClient}.
 *
 * @author Stephane Nicoll
 */
class AccountManagementClientTests {

	private final MockRestServiceServer mockServer;

	private final AccountManagementClient client;

	AccountManagementClientTests() {
		MockServerRestTemplateCustomizer restTemplateCustomizer = new MockServerRestTemplateCustomizer();
		RestTemplateBuilder restTemplateBuilder = new RestTemplateBuilder().customizers(restTemplateCustomizer);
		this.client = new AccountManagementClient(mock(Log.class), restTemplateBuilder);
		this.mockServer = restTemplateCustomizer.getServer();
	}

	@Test
	void provisionAccountOnSupportedCluster() {
		this.mockServer
				.expect(requestToUriTemplate(
						"https://example.com/api/v2/trial/spring-boot-autoconfigure?application={0}&service={1}",
						"unnamed_application", "unnamed_service"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
						.body("{\"url\":\"/us/test123\",\"token\":\"ee479a71-abcd-abcd-abcd-62b0e8416989\"}\n"));
		AccountInfo accountInfo = this.client.provisionAccount("https://example.com", createDefaultApplicationTags());
		assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
		assertThat(accountInfo.determineLoginUrl("https://example.com")).isEqualTo("https://example.com/us/test123");
	}

	@Test
	void provisionAccountOnSupportedClusterWithCustomInfo() {
		MockEnvironment environment = new MockEnvironment()
				.withProperty("wavefront.application.name", "test-application")
				.withProperty("wavefront.application.service", "test-service")
				.withProperty("wavefront.application.cluster", "test-cluster")
				.withProperty("wavefront.application.shard", "test-shard");
		this.mockServer.expect(requestToUriTemplate(
				"https://example.com/api/v2/trial/spring-boot-autoconfigure?application={0}&service={1}&cluster={2}&shard={3}",
				"test-application", "test-service", "test-cluster", "test-shard")).andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
						.body("{\"url\":\"/us/test123\",\"token\":\"ee479a71-abcd-abcd-abcd-62b0e8416989\"}\n"));
		AccountInfo accountInfo = this.client.provisionAccount("https://example.com",
				new ApplicationTagsFactory().createFromEnvironment(environment));
		assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
		assertThat(accountInfo.determineLoginUrl("https://example.com")).isEqualTo("https://example.com/us/test123");
	}

	@Test
	void provisionAccountOnUnsupportedCluster() {
		this.mockServer
				.expect(requestToUriTemplate(
						"https://example.com/api/v2/trial/spring-boot-autoconfigure?application={0}&service={1}",
						"unnamed_application", "unnamed_service"))
				.andExpect(method(HttpMethod.POST)).andRespond(withStatus(HttpStatus.NOT_ACCEPTABLE)
						.contentType(MediaType.APPLICATION_JSON).body("test failure".getBytes()));
		assertThatThrownBy(() -> this.client.provisionAccount("https://example.com", createDefaultApplicationTags()))
				.hasMessageContaining("test failure").isInstanceOf(AccountManagementFailedException.class);
	}

	@Test
	void retrieveAccountOnSupportedCluster() {
		this.mockServer
				.expect(requestToUriTemplate(
						"https://example.com/api/v2/trial/spring-boot-autoconfigure?application={0}&service={1}",
						"unnamed_application", "unnamed_service"))
				.andExpect(method(HttpMethod.GET))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer ee479a71-abcd-abcd-abcd-62b0e8416989"))
				.andRespond(withStatus(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
						.body("{\"url\":\"/us/test123\"}\n"));
		AccountInfo accountInfo = this.client.getExistingAccount("https://example.com", createDefaultApplicationTags(),
				"ee479a71-abcd-abcd-abcd-62b0e8416989");
		assertThat(accountInfo.getApiToken()).isEqualTo("ee479a71-abcd-abcd-abcd-62b0e8416989");
		assertThat(accountInfo.determineLoginUrl("https://example.com")).isEqualTo("https://example.com/us/test123");
	}

	@Test
	void retrieveAccountWithWrongApiToken() {
		this.mockServer
				.expect(requestToUriTemplate(
						"https://example.com/api/v2/trial/spring-boot-autoconfigure?application={0}&service={1}",
						"unnamed_application", "unnamed_service"))
				.andExpect(method(HttpMethod.GET)).andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer wrong-token"))
				.andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
						.body("test failure".getBytes()));
		assertThatThrownBy(() -> this.client.getExistingAccount("https://example.com", createDefaultApplicationTags(),
				"wrong-token")).hasMessageContaining("test failure")
						.isInstanceOf(AccountManagementFailedException.class);
	}

	private ApplicationTags createDefaultApplicationTags() {
		return new ApplicationTagsFactory().createFromEnvironment(new MockEnvironment());
	}

}
