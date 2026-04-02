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

/** API error fields from failed {@link com.protoxon.S4J.SLSAction} requests. */
public interface ApiFailure {

	int statusCode();

	String code();

	String status();

	String detail();

	String hint();

	String requestId();

	int retryAfterSeconds();

	String getMessage();

	// Returns a string in the format {code} {status} : {hint}
	// Used for displaying the error to users
	default String info() {
		StringBuilder left = new StringBuilder();
		String codeStr = code();
		if (codeStr == null || codeStr.isEmpty()) {
			int sc = statusCode();
			if (sc > 0) {
				codeStr = Integer.toString(sc);
			}
		}
		if (codeStr != null && !codeStr.isEmpty()) {
			left.append(codeStr);
		}
		String st = status();
		if (st != null && !st.isEmpty()) {
			if (!left.isEmpty()) {
				left.append(' ');
			}
			left.append(st);
		}
		String right = hint();
		if (left.isEmpty() && (right == null || right.isEmpty())) {
			return statusCode() < 0 ? "Unable to reach the API." : "Request failed.";
		}
		if (left.isEmpty()) {
			return right != null ? right : "";
		}
		if (right == null || right.isEmpty()) {
			return left.toString();
		}
		return left + ": " + right;
	}
}
