

package com.protoxon.S4J.exceptions;

import org.json.JSONObject;

/**
 * Thrown when the API reports a validation or unprocessable request (typically HTTP 422).
 */
public class MissingActionException extends ApiError {

	public MissingActionException(String text, JSONObject json) {
		this(
				ApiError.composeMessage(text, ApiError.buildFields(json, 422), "\t- "),
				ApiError.buildFields(json, 422));
	}

	MissingActionException(String message, ApiError.Fields fields) {
		super(message, fields);
	}
}
