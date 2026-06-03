package io.gauss.augur.circuitbreaker;

/**
 * Thrown when a call is attempted while the circuit breaker is in the
 * {@link CircuitBreakerState#OPEN} state (HU-053).
 *
 * <p>Callers catching this exception should invoke the configured fallback
 * method instead of propagating it to end-users.  The Gauss interceptor layer
 * handles this automatically when {@link io.gauss.core.annotation.CircuitBreaker}
 * is present.
 */
public class CircuitBreakerOpenException extends RuntimeException {

    private final String endpointName;

    public CircuitBreakerOpenException(String endpointName) {
        super("Circuit breaker OPEN for endpoint: " + endpointName);
        this.endpointName = endpointName;
    }

    /** The name of the endpoint whose circuit is open. */
    public String endpointName() {
        return endpointName;
    }
}
