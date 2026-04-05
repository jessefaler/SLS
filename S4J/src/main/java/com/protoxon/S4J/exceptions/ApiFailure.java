

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
