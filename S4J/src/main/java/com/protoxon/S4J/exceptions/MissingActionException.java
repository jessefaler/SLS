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

import org.json.JSONArray;
import org.json.JSONObject;

public class MissingActionException extends SLSException {

	public MissingActionException(String text, JSONObject json) {
		super(formatMessage(text, json));
	}

	public static String formatMessage(String text, JSONObject json) {
		StringBuilder message = new StringBuilder(text + "\n\n");

		// Handle "errors" array format (existing format with meta information)
		if (json.has("errors") && json.get("errors") instanceof JSONArray) {
			JSONArray errorsArray = json.getJSONArray("errors");
			for (int i = 0; i < errorsArray.length(); i++) {
				Object o = errorsArray.get(i);
				if (o instanceof JSONObject obj) {
                    String detail = obj.has("detail") ? obj.getString("detail") : obj.toString();

					if (obj.has("meta") && obj.get("meta") instanceof JSONObject) {
						JSONObject meta = obj.getJSONObject("meta");
						if (meta.has("source_field")) {
							message.append("\t- ")
									.append(detail)
									.append(" (Source: ")
									.append(meta.getString("source_field"))
									.append(")\n");
						} else {
							message.append("\t- ").append(detail).append("\n");
						}
					} else {
						message.append("\t- ").append(detail).append("\n");
					}
				} else {
					message.append("\t- ").append(o.toString()).append("\n");
				}
			}
		}
		// Handle "error" string format
		else if (json.has("error")) {
			message.append("\t- ").append(json.getString("error")).append("\n");
		}
		// Handle "message" string format
		else if (json.has("message")) {
			message.append("\t- ").append(json.getString("message")).append("\n");
		}
		// Show raw JSON if no recognized error format
		else {
			message.append("\t- ").append(json.toString()).append("\n");
		}

		return message.toString();
	}
}
