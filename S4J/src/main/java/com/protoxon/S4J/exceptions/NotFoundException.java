

package com.protoxon.S4J.exceptions;

public class NotFoundException extends ApiError {

	public NotFoundException(String message) {
		super(message, ApiError.Fields.forStatus(404, "404", "Not Found", message, message, ""));
	}

	public NotFoundException(String message, ApiError.Fields fields) {
		super(message, fields);
	}
}
