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

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.wavefront.sdk.common.application.ApplicationTags;
import com.wavefront.sdk.common.application.ApplicationTags.Builder;
import com.wavefront.spring.autoconfigure.WavefrontProperties.Application;

import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.env.Environment;

/**
 * Factory that can be used to create an {@link ApplicationTags}.
 *
 * @author Stephane Nicoll
 */
public class ApplicationTagsFactory {

	private static final String PREFIX = "wavefront.application.";

	private final List<ApplicationTagsBuilderCustomizer> customizers;

	/**
	 * Create an instance with the specified {@link ApplicationTagsBuilderCustomizer
	 * customizers}.
	 * @param customizers the customizers (can be {@code null}).
	 */
	public ApplicationTagsFactory(List<ApplicationTagsBuilderCustomizer> customizers) {
		this.customizers = (customizers != null) ? customizers : Collections.emptyList();
	}

	/**
	 * Create an instance with no further customization.
	 */
	public ApplicationTagsFactory() {
		this(null);
	}

	/**
	 * Create an {@link ApplicationTags} from properties.
	 * @param properties the wavefront properties
	 * @return a matching {@link ApplicationTags}
	 */
	public ApplicationTags createFromProperties(WavefrontProperties properties) {
		Application application = properties.getApplication();
		Builder builder = new Builder(application.getName(), application.getService());
		PropertyMapper mapper = PropertyMapper.get().alwaysApplyingWhenNonNull();
		mapper.from(application::getCluster).to(builder::cluster);
		mapper.from(application::getShard).to(builder::shard);
		return customize(builder).build();
	}

	/**
	 * Create an {@link ApplicationTags} from the {@link Environment}.
	 * @param environment the environment
	 * @return a matching {@link ApplicationTags}
	 */
	public ApplicationTags createFromEnvironment(Environment environment) {
		String name = getValue(environment, "name", () -> "unnamed_application");
		String service = getValue(environment, "service", () -> "unnamed_service");
		return customize(new Builder(name, service).cluster(getValue(environment, "cluster", () -> null))
				.shard(getValue(environment, "shard", () -> null))).build();
	}

	private Builder customize(Builder builder) {
		this.customizers.forEach((customizer) -> customizer.customize(builder));
		return builder;
	}

	private String getValue(Environment environment, String name, Supplier<String> fallback) {
		String value = environment.getProperty(PREFIX + name);
		return (value != null) ? value : fallback.get();
	}

}
