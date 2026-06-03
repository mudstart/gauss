package io.gauss.augur.circuitbreaker;

import java.time.Duration;

/**
 * Immutable configuration for a single circuit breaker (HU-053).
 *
 * @param failureThreshold  consecutive failures required to open the circuit
 * @param delay             how long to stay OPEN before moving to HALF_OPEN
 * @param fallbackMethod    name of the fallback method in the endpoint class
 */
public record CircuitBreakerPolicy(
        int      failureThreshold,
        Duration delay,
        String   fallbackMethod
) {

    /**
     * Creates a policy from the values declared on a
     * {@link io.gauss.core.annotation.CircuitBreaker} annotation, parsing the
     * {@code delay} string (e.g., {@code "30s"}, {@code "1m"}).
     */
    public static CircuitBreakerPolicy of(int threshold, String delayStr, String fallback) {
        return new CircuitBreakerPolicy(threshold, parseDelay(delayStr), fallback);
    }

    /** Creates a policy with no fallback method. */
    public static CircuitBreakerPolicy of(int threshold, String delayStr) {
        return of(threshold, delayStr, "");
    }

    // -------------------------------------------------------------------------

    private static Duration parseDelay(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Delay must not be blank");
        char unit = s.charAt(s.length() - 1);
        long value = Long.parseLong(s.substring(0, s.length() - 1));
        return switch (unit) {
            case 's' -> Duration.ofSeconds(value);
            case 'm' -> Duration.ofMinutes(value);
            case 'h' -> Duration.ofHours(value);
            default  -> throw new IllegalArgumentException("Unknown delay unit '" + unit + "' in: " + s);
        };
    }
}
