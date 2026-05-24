

package com.protoxon.S4J.utils;

import java.util.Collection;

public final class Checks {

	public static void check(boolean expression, String message) {
		if (!expression) throw new IllegalArgumentException(message);
	}

	public static void notNull(Object o, String name) {
		if (o == null) {
			throw new IllegalArgumentException(name + " cannot be null!");
		}
	}

	public static void notBlank(String s, String name) {
		notNull(s, name);
		if (s.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be empty!");
		}
	}

	public static void notNegative(final int n, final String name) {
		if (n < 0) throw new IllegalArgumentException(name + " may not be negative!");
	}

	public static void notNumeric(Object o, String name) {
		notNull(o, name);
		if (o instanceof String) {
			notBlank((String) o, name);
		}
		try {
			Long.parseLong(o.toString());
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException(name + " must be a number!");
		}
	}

	public static void notEmpty(Collection<?> collection, String name) {
		notNull(collection, name);
		if (collection.isEmpty()) {
			throw new IllegalArgumentException(name + " cannot be empty!");
		}
	}
}
