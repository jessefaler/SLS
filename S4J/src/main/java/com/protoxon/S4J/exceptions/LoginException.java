

package com.protoxon.S4J.exceptions;

public class LoginException extends ApiError {

	public LoginException(String message) {
		super(message, ApiError.Fields.forStatus(403, "403", "Forbidden", message, message, ""));
	}

	public LoginException(String message, ApiError.Fields fields) {
		super(message, fields);
	}
}
