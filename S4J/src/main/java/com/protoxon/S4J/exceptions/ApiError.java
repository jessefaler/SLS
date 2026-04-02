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
import com.protoxon.S4J.utils.S4JLogger;

import java.util.concurrent.CompletionException;

import org.json.JSONObject;
import org.slf4j.Logger;

/**
 * Structured API failure for Protocube JSON error responses ({@code code}, {@code status}, {@code detail},
 * {@code hint}, optional {@code request_id}, {@code retry_after} on 429).
 */
public abstract class ApiError extends SLSException implements ApiFailure {

	private static final Logger LOG = S4JLogger.getLogger(ApiError.class);

	private final Fields fields;

	/**
	 * Logs an {@link ApiFailure} being delivered to application code (failure callback or exceptional completion).
	 *
	 * @param route   HTTP route if known, or null
	 * @param failure the failure being returned
	 */
	public static void logFailure(Route.CompiledRoute route, ApiFailure failure) {
		if (route != null) {
			LOG.warn("{} {} - {}: {}", route.getMethod(), route.getCompiledRoute(), failure.info(), failure.detail());
		} else {
			LOG.warn("{}: {}", failure.info(), failure.detail());
		}
	}

	/**
	 * Parsed error fields aligned with Protocube {@code httperror.Error} (plus {@code request_id},
	 * {@code retry_after} on 429).
	 */
	public record Fields(
			int statusCode,
			String code,
			String status,
			String detail,
			String hint,
			String requestId,
			int retryAfterSeconds) {

		public static Fields transport(String detail) {
			String d = detail == null ? "" : detail;
			return new Fields(Response.ERROR_CODE, "", "", d, "", "", 0);
		}

		public static Fields forStatus(
				int statusCode,
				String code,
				String statusText,
				String detail,
				String hint,
				String requestId) {
			String c =
					(code != null && !code.isEmpty())
							? code
							: (statusCode > 0 ? Integer.toString(statusCode) : "");
			return new Fields(
					statusCode,
					c,
					statusText != null ? statusText : "",
					detail != null ? detail : "",
					hint != null ? hint : "",
					requestId != null ? requestId : "",
					0);
		}

		/** Rate limit: {@code retryAfterMs} is backoff time in milliseconds. */
		public static Fields rateLimited(long retryAfterMs, String detailMessage) {
			int sec =
					retryAfterMs <= 0
							? 0
							: (retryAfterMs >= 1000
									? (int) (retryAfterMs / 1000)
									: Math.max(1, (int) retryAfterMs));
			String d = detailMessage != null ? detailMessage : "";
			return new Fields(429, "429", "Too Many Requests", d, "", "", sec);
		}
	}

	protected ApiError(String message, Fields fields) {
		super(message);
		this.fields = fields;
	}

	public final Fields fields() {
		return fields;
	}

	@Override
	public final int statusCode() {
		return fields.statusCode();
	}

	@Override
	public final String code() {
		return fields.code();
	}

	@Override
	public final String status() {
		return fields.status();
	}

	@Override
	public final String detail() {
		return fields.detail();
	}

	@Override
	public final String hint() {
		return fields.hint();
	}

	@Override
	public final String requestId() {
		return fields.requestId();
	}

	@Override
	public final int retryAfterSeconds() {
		return fields.retryAfterSeconds();
	}

	/**
	 * Builds a multi-line message from a prefix and {@link Fields#detail()} (one {@code linePrefix} per line of
	 * detail).
	 */
	public static String composeMessage(String text, Fields f, String linePrefix) {
		if (f.detail().isEmpty()) return text;
		StringBuilder sb = new StringBuilder(text).append("\n\n");
		for (String line : f.detail().split("\n", -1)) {
			if (!line.isEmpty()) {
				sb.append(linePrefix).append(line).append('\n');
			}
		}
		return sb.toString().trim();
	}

	/**
	 * Parses Protocube standard JSON error bodies ({@code detail} with {@code code} or {@code status}; optional
	 * {@code hint}, {@code request_id}, {@code retry_after}).
	 */
	public static Fields buildFields(JSONObject json, int httpStatus) {
		if (json == null) {
			return Fields.forStatus(httpStatus, "", "", "", "", "");
		}
		if (json.has("detail") && (json.has("code") || json.has("status"))) {
			String code = json.optString("code", "");
			String status = json.optString("status", "");
			String detail = json.optString("detail", "");
			String hint = json.optString("hint", "");
			String requestId = json.optString("request_id", "");
			int retryAfter = json.optInt("retry_after", 0);
			return new Fields(httpStatus, code, status, detail, hint, requestId, retryAfter);
		}
		if (json.has("detail")) {
			return new Fields(
					httpStatus,
					httpStatus > 0 ? Integer.toString(httpStatus) : "",
					"",
					json.optString("detail", ""),
					json.optString("hint", ""),
					json.optString("request_id", ""),
					json.optInt("retry_after", 0));
		}
		return Fields.forStatus(
				httpStatus,
				httpStatus > 0 ? Integer.toString(httpStatus) : "",
				"",
				json.toString(),
				"",
				"");
	}

	static String defaultPrefix(int httpCode) {
		return switch (httpCode) {
			case 403 -> "The request was not authorized.";
			case 404 -> "The requested resource was not found.";
			case 422 -> "The request could not be processed.";
			case 500 -> "The server encountered an error.";
			default -> String.format("S4J has encountered a %d error.", httpCode);
		};
	}

	/**
	 * Maps an HTTP error response body to a typed {@link ApiError} using {@link #defaultPrefix(int)}.
	 */
	public static ApiError fromResponse(Response response) {
		return fromResponse(defaultPrefix(response.getCode()), response);
	}

	/**
	 * Maps an HTTP error response body to a typed {@link ApiError} (403/404/422/500 use specific subclasses).
	 */
	public static ApiError fromResponse(String prefix, Response response) {
		int code = response.getCode();
		String raw = response.getRawObject();
		JSONObject json = parseJsonObject(raw);
		if (json != null) {
			Fields f = buildFields(json, code);
			String linePrefix = code == 422 ? "\t- " : "  - ";
			String message = composeMessage(prefix, f, linePrefix);
			return forStatusCode(code, message, f);
		}
		Fields empty = Fields.forStatus(code, code > 0 ? Integer.toString(code) : "", "", "", "", "");
		String message = prefix;
		if (raw != null && !raw.trim().isEmpty()) {
			message = message + "\n\nResponse body: " + raw;
			empty = Fields.forStatus(code, Integer.toString(code), "", raw, "", "");
		}
		return forStatusCode(code, message, empty);
	}

	private static JSONObject parseJsonObject(String raw) {
		if (raw == null || raw.trim().isEmpty()) return null;
		try {
			return new JSONObject(raw);
		} catch (Exception ignored) {
			return null;
		}
	}

	static ApiError forStatusCode(int httpCode, String message, Fields f) {
		return switch (httpCode) {
			case 403 -> new LoginException(message, f);
			case 404 -> new NotFoundException(message, f);
			case 422 -> new MissingActionException(message, f);
			case 500 -> new ServerException(message, f);
			default -> new ParsedHttpError(message, f);
		};
	}

	/**
	 * Normalizes any throwable to {@link ApiError} for failure callbacks (unwraps {@link CompletionException}).
	 */
	public static ApiError coerce(Throwable t) {
		if (t instanceof ApiError ae) {
			return ae;
		}
		if (t instanceof CompletionException ce && ce.getCause() != null) {
			return coerce(ce.getCause());
		}
		String msg = t.getMessage();
		if (msg == null || msg.isEmpty()) {
			msg = t.getClass().getSimpleName();
		}
		return new WrappedApiError(t, Fields.transport(msg));
	}

	private static final class ParsedHttpError extends ApiError {
		ParsedHttpError(String message, Fields fields) {
			super(message, fields);
		}
	}

	private static final class WrappedApiError extends ApiError {
		WrappedApiError(Throwable cause, Fields fields) {
			super(fields.detail(), fields);
			initCause(cause);
		}
	}
}
