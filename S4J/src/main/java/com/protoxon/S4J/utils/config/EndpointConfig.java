package com.protoxon.S4J.utils.config;

import com.protoxon.S4J.utils.Checks;

public record EndpointConfig(String url, String token) {
	public EndpointConfig {
		Checks.notBlank(token, "API Key");
		Checks.notBlank(url, "SLS API URL");
	}
}
