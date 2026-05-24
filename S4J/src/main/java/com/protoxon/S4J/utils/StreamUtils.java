

package com.protoxon.S4J.utils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collector;
import java.util.stream.Collectors;

public final class StreamUtils {

	public static <T> Collector<T, ?, List<T>> toUnmodifiableList() {
		return Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList);
	}

	public static boolean compareString(String a, String b, boolean caseSensitive) {
		return caseSensitive ? a.equals(b) : a.equalsIgnoreCase(b);
	}
}
