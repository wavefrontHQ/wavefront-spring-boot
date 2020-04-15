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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Tests for {@link AccountManagementEnvironmentPostProcessor}.
 *
 * @author Stephane Nicoll
 */
@ExtendWith(OutputCaptureExtension.class)
class AccountManagementEnvironmentPostProcessorTests {

	private static final String API_TOKEN_PROPERTY = "management.metrics.export.wavefront.api-token";

	private static final String URI_PROPERTY = "management.metrics.export.wavefront.uri";

	private final SpringApplication application = mock(SpringApplication.class);

	@Test
	void accountProvisioningIsNotNeededWhenApiTokenExists() {
		ConfigurableEnvironment environment = mock(ConfigurableEnvironment.class);
		given(environment.getProperty(API_TOKEN_PROPERTY)).willReturn("test");
		new AccountManagementEnvironmentPostProcessor().postProcessEnvironment(environment, this.application);
		verify(environment).getProperty(API_TOKEN_PROPERTY);
		verifyNoMoreInteractions(environment);
	}

	@Test
	void accountProvisioningIsNotNeededWhenApiTokenIsNotNecessary() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(URI_PROPERTY, "proxy://example.com:2878");
		new AccountManagementEnvironmentPostProcessor().postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isNull();
	}

	@Test
	void existingAccountIsConfiguredWhenApiTokenFileExists(CapturedOutput output) throws IOException {
		Resource apiTokenResource = mockApiTokenResource("abc-def");
		MockEnvironment environment = new MockEnvironment();
		TestAccountManagementEnvironmentPostProcessor postProcessor = TestAccountManagementEnvironmentPostProcessor
				.forExistingAccount(apiTokenResource, () -> new AccountInfo("abc-def", "/us/test1"));
		postProcessor.postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isEqualTo("abc-def");
		assertThat(environment.getProperty(URI_PROPERTY)).isEqualTo("https://wavefront.surf");
		postProcessor.onApplicationEvent(mockApplicationStartedEvent());
		assertThat(output).contains("Your existing Wavefront account information has been restored from disk.\n" + "\n"
				+ "To share this account, make sure the following is added to your configuration:\n\n"
				+ "\tmanagement.metrics.export.wavefront.api-token=abc-def\n"
				+ "\tmanagement.metrics.export.wavefront.uri=https://wavefront.surf\n\n"
				+ "Connect to your Wavefront dashboard using this one-time use link:\n"
				+ "https://wavefront.surf/us/test1\n");
	}

	@Test
	void existingAccountRetrievalFailureLogsWarning(CapturedOutput output) throws IOException {
		Resource apiTokenResource = mockApiTokenResource("abc-def");
		MockEnvironment environment = new MockEnvironment().withProperty(URI_PROPERTY, "https://example.com");
		TestAccountManagementEnvironmentPostProcessor postProcessor = TestAccountManagementEnvironmentPostProcessor
				.forExistingAccount(apiTokenResource, () -> {
					throw new AccountManagementFailedException("test message");
				});
		postProcessor.postProcessEnvironment(environment, this.application);
		postProcessor.onApplicationEvent(mockApplicationStartedEvent());
		assertThat(output)
				.contains("Failed to retrieve existing account information from https://example.com. The error was:\n"
						+ "\n" + "test message\n");
	}

	@Test
	void accountProvisioningIsRequiredWhenApiTokenFileDoesNotExist(@TempDir Path directory, CapturedOutput output) {
		Path apiTokenFile = directory.resolve("test.token");
		assertThat(apiTokenFile).doesNotExist();
		MockEnvironment environment = new MockEnvironment();
		TestAccountManagementEnvironmentPostProcessor postProcessor = TestAccountManagementEnvironmentPostProcessor
				.forNewAccount(new PathResource(apiTokenFile), () -> new AccountInfo("abc-def", "/us/test"));
		postProcessor.postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isEqualTo("abc-def");
		assertThat(environment.getProperty(URI_PROPERTY)).isEqualTo("https://wavefront.surf");
		assertThat(apiTokenFile).exists();
		assertThat(apiTokenFile).hasContent("abc-def");
		postProcessor.onApplicationEvent(mockApplicationStartedEvent());
		assertThat(output).contains(
				"A Wavefront account has been provisioned successfully and the API token has been saved to disk.\n\n"
						+ "To share this account, make sure the following is added to your configuration:\n\n"
						+ "\tmanagement.metrics.export.wavefront.api-token=abc-def\n"
						+ "\tmanagement.metrics.export.wavefront.uri=https://wavefront.surf\n\n"
						+ "Connect to your Wavefront dashboard using this one-time use link:\n"
						+ "https://wavefront.surf/us/test\n");
	}

	@Test
	void accountProvisioningFailureLogsWarning(CapturedOutput output) {
		Resource apiTokenResource = mock(Resource.class);
		given(apiTokenResource.isReadable()).willReturn(false);
		MockEnvironment environment = new MockEnvironment();
		TestAccountManagementEnvironmentPostProcessor postProcessor = TestAccountManagementEnvironmentPostProcessor
				.forNewAccount(apiTokenResource, () -> {
					throw new AccountManagementFailedException("test message");
				});
		postProcessor.postProcessEnvironment(environment, this.application);
		verify(apiTokenResource).isReadable();
		verifyNoMoreInteractions(apiTokenResource);
		postProcessor.onApplicationEvent(mockApplicationStartedEvent());
		assertThat(output)
				.contains("Failed to auto-negotiate a Wavefront api token from https://wavefront.surf. The error was:\n"
						+ "\n" + "test message\n");
	}

	@Test
	void accountProvisioningDoesNotFailWhenReadingApiTokenFileFails() throws IOException {
		Resource apiTokenResource = mock(Resource.class);
		given(apiTokenResource.isReadable()).willReturn(true);
		given(apiTokenResource.isFile()).willReturn(false);
		given(apiTokenResource.getInputStream()).willThrow(new IOException("test exception"));
		MockEnvironment environment = new MockEnvironment();
		TestAccountManagementEnvironmentPostProcessor
				.forNewAccount(apiTokenResource, () -> new AccountInfo("test", "test"))
				.postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isEqualTo("test");
		assertThat(environment.getProperty(URI_PROPERTY)).isEqualTo("https://wavefront.surf");
	}

	@Test
	void accountProvisioningDoesNotFailWhenWritingApiTokenFails() throws IOException {
		Resource apiTokenResource = mock(Resource.class);
		given(apiTokenResource.isReadable()).willReturn(false);
		given(apiTokenResource.isFile()).willReturn(true);
		given(apiTokenResource.getFile()).willThrow(new IOException("test exception"));
		MockEnvironment environment = new MockEnvironment();
		TestAccountManagementEnvironmentPostProcessor
				.forNewAccount(apiTokenResource, () -> new AccountInfo("test", "test"))
				.postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isEqualTo("test");
		assertThat(environment.getProperty(URI_PROPERTY)).isEqualTo("https://wavefront.surf");
	}

	@Test
	void uriIsNotSetIfACustomUriIsSet() throws IOException {
		Resource apiTokenResource = mockApiTokenResource("abc-def");
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty(URI_PROPERTY, "https://example.com");
		TestAccountManagementEnvironmentPostProcessor
				.forExistingAccount(apiTokenResource, () -> new AccountInfo("abc-def", "test"))
				.postProcessEnvironment(environment, this.application);
		assertThat(environment.getProperty(API_TOKEN_PROPERTY)).isEqualTo("abc-def");
		assertThat(environment.getProperty(URI_PROPERTY)).isEqualTo("https://example.com");
		assertThat(environment.getPropertySources().get("wavefront").getProperty(URI_PROPERTY)).isNull();
	}

	@Test
	void defaultApiTokenFile() {
		Resource localApiTokenResource = new AccountManagementEnvironmentPostProcessor().getLocalApiTokenResource();
		assertThat(localApiTokenResource.getFilename()).isEqualTo(".wavefront_token");
	}

	@Test
	void provisionAccountInvokeClientWithSuppliedArguments() {
		AccountManagementClient client = mock(AccountManagementClient.class);
		String clusterUri = "https://example.com";
		ApplicationTags applicationTags = mock(ApplicationTags.class);
		new AccountManagementEnvironmentPostProcessor().provisionAccount(client, clusterUri, applicationTags);
		verify(client).provisionAccount(clusterUri, applicationTags);
	}

	@Test
	void getExistingAccountInvokeClientWithSuppliedArguments() {
		AccountManagementClient client = mock(AccountManagementClient.class);
		String clusterUri = "https://example.com";
		ApplicationTags applicationTags = mock(ApplicationTags.class);
		String apiToken = "abc-def";
		new AccountManagementEnvironmentPostProcessor().getExistingAccount(client, clusterUri, applicationTags,
				apiToken);
		verify(client).getExistingAccount(clusterUri, applicationTags, apiToken);
	}

	private Resource mockApiTokenResource(String apiToken) throws IOException {
		Resource apiTokenResource = mock(Resource.class);
		given(apiTokenResource.isReadable()).willReturn(true);
		given(apiTokenResource.getInputStream())
				.willReturn(new ByteArrayInputStream(apiToken.getBytes(StandardCharsets.UTF_8)));
		return apiTokenResource;
	}

	private ApplicationStartedEvent mockApplicationStartedEvent() {
		return new ApplicationStartedEvent(this.application, new String[0], mock(ConfigurableApplicationContext.class));
	}

	static class TestAccountManagementEnvironmentPostProcessor extends AccountManagementEnvironmentPostProcessor {

		private final Resource localApiTokenResource;

		private final Supplier<AccountInfo> accountProvisioning;

		private final Supplier<AccountInfo> existingAccount;

		TestAccountManagementEnvironmentPostProcessor(Resource localApiTokenResource,
				Supplier<AccountInfo> existingAccount, Supplier<AccountInfo> accountProvisioning) {
			this.localApiTokenResource = localApiTokenResource;
			this.existingAccount = existingAccount;
			this.accountProvisioning = accountProvisioning;
		}

		static TestAccountManagementEnvironmentPostProcessor forExistingAccount(Resource localApiToResource,
				Supplier<AccountInfo> existingAccount) {
			return new TestAccountManagementEnvironmentPostProcessor(localApiToResource, existingAccount, () -> {
				throw new IllegalArgumentException("Should not be called");
			});
		}

		static TestAccountManagementEnvironmentPostProcessor forNewAccount(Resource localApiToResource,
				Supplier<AccountInfo> accountProvisioning) {
			return new TestAccountManagementEnvironmentPostProcessor(localApiToResource, () -> {
				throw new IllegalArgumentException("Should not be called");
			}, accountProvisioning);
		}

		@Override
		protected Resource getLocalApiTokenResource() {
			return this.localApiTokenResource;
		}

		@Override
		protected AccountInfo getExistingAccount(AccountManagementClient client, String clusterUri,
				ApplicationTags applicationTags, String apiToken) {
			return this.existingAccount.get();
		}

		@Override
		protected AccountInfo provisionAccount(AccountManagementClient client, String clusterUri,
				ApplicationTags applicationTags) {
			return this.accountProvisioning.get();
		}

	}

}
