

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
