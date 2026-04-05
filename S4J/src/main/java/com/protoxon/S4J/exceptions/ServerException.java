

package com.protoxon.S4J.exceptions;

public class ServerException extends ApiError {

	public ServerException(String message) {
		super(message, ApiError.Fields.forStatus(500, "500", "Internal Server Error", message, message, ""));
	}

	public ServerException(String message, ApiError.Fields fields) {
		super(message, fields);
	}
}
