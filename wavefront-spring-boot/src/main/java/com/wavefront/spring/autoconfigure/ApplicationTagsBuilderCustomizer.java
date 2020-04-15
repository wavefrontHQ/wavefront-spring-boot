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

import com.wavefront.sdk.common.application.ApplicationTags;

/**
 * Callback interface that can be implemented by beans wishing to customize the
 * {@link ApplicationTags} via a {@link ApplicationTags.Builder} whilst retaining default
 * auto-configuration.
 *
 * @author Stephane Nicoll
 */
@FunctionalInterface
public interface ApplicationTagsBuilderCustomizer {

	/**
	 * Customize the {@link ApplicationTags.Builder}.
	 * @param builder the builder to customize
	 */
	void customize(ApplicationTags.Builder builder);

}
