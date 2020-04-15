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

import org.springframework.web.util.UriComponentsBuilder;

/**
 * Information of a provisioned account.
 *
 * @author Stephane Nicoll
 */
class AccountInfo {

	private final String apiToken;

	private final String loginUri;

	AccountInfo(String apiToken, String loginUri) {
		this.apiToken = apiToken;
		this.loginUri = loginUri;
	}

	/**
	 * Return the api token of the account.
	 * @return the api token
	 */
	String getApiToken() {
		return this.apiToken;
	}

	/**
	 * Determine the one time login url based on the specified Wavefront cluster.
	 * @param clusterUri the uri of the cluster
	 * @return the one time login url for that account
	 */
	String determineLoginUrl(String clusterUri) {
		return UriComponentsBuilder.fromUriString(clusterUri).path(this.loginUri).build().toUriString();
	}

}
