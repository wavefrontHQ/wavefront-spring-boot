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

import java.util.stream.Collectors;

import com.wavefront.sdk.common.application.ApplicationTags;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.actuate.autoconfigure.metrics.export.wavefront.WavefrontMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@link EnableAutoConfiguration Auto-configuration} to integrate with Wavefront metrics
 * and tracing.
 *
 * @author Stephane Nicoll
 */
@Configuration
@ConditionalOnClass(ApplicationTags.class)
@EnableConfigurationProperties(WavefrontProperties.class)
@AutoConfigureAfter(WavefrontMetricsExportAutoConfiguration.class)
@Import({ WavefrontMetricsConfiguration.class, WavefrontTracingConfiguration.class })
public class WavefrontAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public ApplicationTags wavefrontApplicationTags(WavefrontProperties properties,
			ObjectProvider<ApplicationTagsBuilderCustomizer> customizers) {
		return new ApplicationTagsFactory(customizers.orderedStream().collect(Collectors.toList()))
				.createFromProperties(properties);
	}

}
