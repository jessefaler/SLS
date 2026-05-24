

package com.protoxon.S4J.utils;

@FunctionalInterface
public interface Procedure<T> {

	boolean execute(T value);
}
