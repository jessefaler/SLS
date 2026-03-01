package net.slimelabs.vsls.utils;

import java.time.Duration;

/**
 * Configuration for retry behavior with exponential backoff.
 */
public record RetryConfig(
        int maxRetries,
        Duration initialDelay,
        Duration maxDelay
) {
    private static final int DEFAULT_MAX_RETRIES = 3;
    private static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(16);

    /**
     * Default config: 3 retries, 1s initial delay, 16s max delay (exponential backoff).
     */
    public static final RetryConfig DEFAULT = new RetryConfig(
            DEFAULT_MAX_RETRIES,
            DEFAULT_INITIAL_DELAY,
            DEFAULT_MAX_DELAY
    );

    public RetryConfig {
        if (maxRetries < 1) throw new IllegalArgumentException("maxRetries must be >= 1");
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            throw new IllegalArgumentException("initialDelay must be positive");
        }
        if (maxDelay.isNegative() || maxDelay.compareTo(initialDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= initialDelay");
        }
    }

    /**
     * Delay before the (1-based) attempt. Attempt 1 = 0, attempt 2 = initialDelay, attempt 3 = 2*initialDelay, etc., capped at maxDelay.
     */
    public long delayMillisBeforeAttempt(int attempt) {
        if (attempt <= 1) return 0;
        long delay = initialDelay.toMillis() * (1L << (attempt - 2));
        return Math.min(delay, maxDelay.toMillis());
    }
}
