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

package com.wavefront.spring.autoconfigure;

import java.util.Arrays;
import java.util.Collections;

import com.wavefront.sdk.common.application.ApplicationTags;
import org.junit.jupiter.api.Test;

import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/**
 * Tests for {@link ApplicationTagsFactory}.
 *
 * @author Stephane Nicoll
 */
class ApplicationTagsFactoryTests {

	private final ApplicationTagsFactory factory = new ApplicationTagsFactory();

	@Test
	void applicationTagsFromEmptyEnvironment() {
		ApplicationTags applicationTags = this.factory.createFromEnvironment(new MockEnvironment());
		assertThat(applicationTags.getApplication()).isEqualTo("unnamed_application");
		assertThat(applicationTags.getService()).isEqualTo("unnamed_service");
		assertThat(applicationTags.getCluster()).isNull();
		assertThat(applicationTags.getShard()).isNull();
	}

	@Test
	void applicationTagsFromEnvironmentWithConfiguredApplication() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.name", "wavefront-app");
		ApplicationTags applicationTags = this.factory.createFromEnvironment(environment);
		assertThat(applicationTags.getApplication()).isEqualTo("wavefront-app");
	}

	@Test
	void applicationTagsFromEnvironmentWithConfiguredService() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.service", "test-service");
		ApplicationTags applicationTags = this.factory.createFromEnvironment(environment);
		assertThat(applicationTags.getService()).isEqualTo("test-service");
	}

	@Test
	void applicationTagsFromEnvironmentWithConfiguredCluster() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.cluster", "test-cluster");
		ApplicationTags applicationTags = this.factory.createFromEnvironment(environment);
		assertThat(applicationTags.getCluster()).isEqualTo("test-cluster");
	}

	@Test
	void applicationTagsFromEnvironmentWithConfiguredShard() {
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.shard", "test-shard");
		ApplicationTags applicationTags = this.factory.createFromEnvironment(environment);
		assertThat(applicationTags.getShard()).isEqualTo("test-shard");
	}

	@Test
	void applicationTagsFromEmptyProperties() {
		ApplicationTags applicationTags = this.factory.createFromProperties(new WavefrontProperties());
		assertThat(applicationTags.getApplication()).isEqualTo("unnamed_application");
		assertThat(applicationTags.getService()).isEqualTo("unnamed_service");
		assertThat(applicationTags.getCluster()).isNull();
		assertThat(applicationTags.getShard()).isNull();
	}

	@Test
	void applicationTagsFromPropertiesWithConfiguredApplication() {
		WavefrontProperties properties = new WavefrontProperties();
		properties.getApplication().setName("wavefront-app");
		ApplicationTags applicationTags = this.factory.createFromProperties(properties);
		assertThat(applicationTags.getApplication()).isEqualTo("wavefront-app");
	}

	@Test
	void applicationTagsFromPropertiesWithConfiguredService() {
		WavefrontProperties properties = new WavefrontProperties();
		properties.getApplication().setService("test-service");
		ApplicationTags applicationTags = this.factory.createFromProperties(properties);
		assertThat(applicationTags.getService()).isEqualTo("test-service");
	}

	@Test
	void applicationTagsFromPropertiesWithConfiguredCluster() {
		WavefrontProperties properties = new WavefrontProperties();
		properties.getApplication().setCluster("test-cluster");
		ApplicationTags applicationTags = this.factory.createFromProperties(properties);
		assertThat(applicationTags.getCluster()).isEqualTo("test-cluster");
	}

	@Test
	void applicationTagsFromPropertiesWithConfiguredShard() {
		WavefrontProperties properties = new WavefrontProperties();
		properties.getApplication().setShard("test-shard");
		ApplicationTags applicationTags = this.factory.createFromProperties(properties);
		assertThat(applicationTags.getShard()).isEqualTo("test-shard");
	}

	@Test
	void applicationTagsWithSingleCustomizer() {
		ApplicationTagsFactory customFactory = new ApplicationTagsFactory(Collections.singletonList(
				(builder) -> builder.shard("overridden-shard").customTags(Collections.singletonMap("test", "value"))));
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.shard", "test-shard");
		ApplicationTags applicationTags = customFactory.createFromEnvironment(environment);
		assertThat(applicationTags.getApplication()).isEqualTo("unnamed_application");
		assertThat(applicationTags.getService()).isEqualTo("unnamed_service");
		assertThat(applicationTags.getCluster()).isNull();
		assertThat(applicationTags.getShard()).isEqualTo("overridden-shard");
		assertThat(applicationTags.getCustomTags()).containsOnly(entry("test", "value"));
	}

	@Test
	void applicationTagsExecuteCustomizersInOrder() {
		ApplicationTagsFactory customFactory = new ApplicationTagsFactory(Arrays.asList(
				(builder) -> builder.shard("overridden-shard").customTags(Collections.singletonMap("test", "value")),
				(builder) -> builder.shard("test")));
		MockEnvironment environment = new MockEnvironment();
		environment.setProperty("wavefront.application.shard", "test-shard");
		ApplicationTags applicationTags = customFactory.createFromEnvironment(environment);
		assertThat(applicationTags.getApplication()).isEqualTo("unnamed_application");
		assertThat(applicationTags.getService()).isEqualTo("unnamed_service");
		assertThat(applicationTags.getCluster()).isNull();
		assertThat(applicationTags.getShard()).isEqualTo("test");
		assertThat(applicationTags.getCustomTags()).containsOnly(entry("test", "value"));
	}

}
