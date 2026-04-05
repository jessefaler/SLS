

package com.protoxon.S4J.exceptions;

/**
 * Generic HTTP client failure without a structured API body (e.g. connection errors).
 * <p>When the API returns a JSON error body, failures typically use a more specific {@link ApiError} subtype.
 */
public class HttpException extends ApiError {

	public HttpException(String message) {
		super(message, ApiError.Fields.transport(message));
	}
}
