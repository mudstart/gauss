package io.gauss.augur.circuitbreaker;

/**
 * Lifecycle states of a {@link EndpointCircuitBreaker} (HU-053).
 *
 * <ul>
 *   <li>{@link #CLOSED}    — normal operation; calls are forwarded to the real method.</li>
 *   <li>{@link #OPEN}      — too many failures; calls are immediately redirected to the
 *                            fallback and the circuit waits for the configured delay.</li>
 *   <li>{@link #HALF_OPEN} — delay elapsed; one probe call is allowed to test recovery.
 *                            Success transitions to CLOSED; failure back to OPEN.</li>
 * </ul>
 */
public enum CircuitBreakerState {
    CLOSED,
    OPEN,
    HALF_OPEN
}
