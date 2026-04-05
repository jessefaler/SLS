

package com.protoxon.S4J.utils.config;

import okhttp3.OkHttpClient;

public final class SessionConfig {

	private final OkHttpClient httpClient;

	public SessionConfig(OkHttpClient httpClient) {
		if (httpClient == null) httpClient = new OkHttpClient();

		this.httpClient = httpClient;
	}

	public OkHttpClient getHttpClient() {
		return httpClient;
	}

}
