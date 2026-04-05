

package com.protoxon.S4J.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class serves as a LoggerFactory for S4J internals.
 * <br>It will either return a Logger from a SLF4J implementation via {@link LoggerFactory} if present,
 * or an instance of a custom {@link SimpleLogger} (From slf4j-simple).
 */
public class S4JLogger {
	// thanks jda

	/**
	 * Marks whether a SLF4J <code>StaticLoggerBinder</code> (pre 1.8.x) or
	 * <code>SLF4JServiceProvider</code> implementation (1.8.x+) was found. If false, S4J will use its fallback logger.
	 * <br>This variable is initialized during static class initialization.
	 */
	public static final boolean SLF4J_ENABLED;

	static {
		boolean tmp = false;

		try {
			Class.forName("org.slf4j.impl.StaticLoggerBinder");
			tmp = true;
		} catch (ClassNotFoundException eStatic) {
			// there was no static logger binder (SLF4J pre-1.8.x)

			try {
				Class<?> serviceProviderInterface = Class.forName("org.slf4j.spi.SLF4JServiceProvider");

				// check if there is a service implementation for the service, indicating a provider for SLF4J 1.8.x+ is
				// installed
				tmp = ServiceLoader.load(serviceProviderInterface).iterator().hasNext();
			} catch (ClassNotFoundException eService) {
				// there was no service provider interface (SLF4J 1.8.x+)
				// let's print a warning of missing implementation
				LoggerFactory.getLogger(S4JLogger.class);
			}
		}

		SLF4J_ENABLED = tmp;
	}

	private static final Map<String, Logger> LOGS = new HashMap<>();

	/**
	 * Will get the {@link Logger} for the given Class
	 * or create and cache a fallback logger if there is no SLF4J implementation present.
	 * <p>
	 * The fallback logger will be an instance of a slightly modified version of SLF4Js SimpleLogger.
	 *
	 * @param  clazz
	 *         The class used for the Logger name
	 *
	 * @return Logger for given Class
	 */
	public static Logger getLogger(Class<?> clazz) {
		synchronized (LOGS) {
			if (SLF4J_ENABLED) return LoggerFactory.getLogger(clazz);
			return LOGS.computeIfAbsent(clazz.getName(), (n) -> new SimpleLogger(clazz.getSimpleName()));
		}
	}
}
