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

import com.wavefront.opentracing.WavefrontTracer;
import com.wavefront.opentracing.reporting.Reporter;
import com.wavefront.opentracing.reporting.WavefrontSpanReporter;
import com.wavefront.sdk.common.WavefrontSender;
import com.wavefront.sdk.common.application.ApplicationTags;
import io.micrometer.wavefront.WavefrontConfig;
import io.opentracing.Tracer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Configuration for Wavefront tracing.
 *
 * @author Stephane Nicoll
 */
@ConditionalOnClass({ Reporter.class, Tracer.class })
@ConditionalOnProperty(value = "wavefront.tracing.enabled", matchIfMissing = true)
class WavefrontTracingConfiguration {

	@Bean
	@ConditionalOnMissingBean(Tracer.class)
	@ConditionalOnBean(WavefrontSender.class)
	WavefrontTracer wavefrontTracer(WavefrontSender wavefrontSender, ApplicationTags applicationTags,
			WavefrontConfig wavefrontConfig) {
		Reporter spanReporter = new WavefrontSpanReporter.Builder().withSource(wavefrontConfig.source())
				.build(wavefrontSender);
		return new WavefrontTracer.Builder(spanReporter, applicationTags).build();
	}

}
