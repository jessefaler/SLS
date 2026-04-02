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
