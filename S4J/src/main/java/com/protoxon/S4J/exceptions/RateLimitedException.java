/*
 *    Copyright 2021-2022 Matt Malec, and the Pterodactyl4J contributors
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.protoxon.S4J.exceptions;

import com.protoxon.S4J.requests.Response;
import com.protoxon.S4J.requests.Route;

import org.json.JSONObject;

public class RateLimitedException extends ApiError {

	public RateLimitedException(Route.CompiledRoute route, long retryAfterMs) {
		super(
				String.format(
						"The request was rate limited. Retry-After: %d ms  Route: %s",
						retryAfterMs, route.getCompiledRoute()),
				ApiError.Fields.rateLimited(
						retryAfterMs,
						String.format(
								"The request was rate limited. Retry-After: %d ms  Route: %s",
								retryAfterMs, route.getCompiledRoute())));
	}

	/**
	 * Prefers JSON body ({@code httperror.TooManyRequests}: {@code detail}, {@code hint}, {@code retry_after}) when
	 * present; otherwise uses the rate-limit backoff from the client.
	 */
	public static RateLimitedException fromResponse(Route.CompiledRoute route, Response response) {
		long headerMs = response.getRetryAfter();
		String raw = response.getRawObject();
		JSONObject json = null;
		try {
			if (raw != null && !raw.trim().isEmpty()) {
				json = new JSONObject(raw);
			}
		} catch (Exception ignored) {
		}

		ApiError.Fields f;
		if (json != null && json.has("detail") && (json.has("code") || json.has("status"))) {
			f = ApiError.buildFields(json, 429);
			if (f.retryAfterSeconds() == 0 && headerMs > 0) {
				int sec = headerMs >= 1000 ? (int) (headerMs / 1000) : 1;
				f = new ApiError.Fields(
						429, f.code(), f.status(), f.detail(), f.hint(), f.requestId(), sec);
			}
		} else {
			String detailMsg =
					String.format(
							"The request was rate limited. Retry-After: %d ms  Route: %s",
							headerMs, route.getCompiledRoute());
			f = ApiError.Fields.rateLimited(headerMs, detailMsg);
		}

		String message = ApiError.composeMessage("The request was rate limited.", f, "  - ");
		return new RateLimitedException(message, f);
	}

	private RateLimitedException(String message, ApiError.Fields fields) {
		super(message, fields);
	}
}
