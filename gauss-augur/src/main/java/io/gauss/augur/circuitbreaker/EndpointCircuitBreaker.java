package io.gauss.augur.circuitbreaker;

import java.time.Instant;
import java.util.function.Supplier;

/**
 * Per-endpoint circuit breaker state machine (HU-053).
 *
 * <p>Tracks consecutive failures and transitions through three states:
 * <ol>
 *   <li>{@link CircuitBreakerState#CLOSED} — normal operation.</li>
 *   <li>{@link CircuitBreakerState#OPEN}   — circuit tripped; calls rejected until
 *       {@link CircuitBreakerPolicy#delay()} elapses.</li>
 *   <li>{@link CircuitBreakerState#HALF_OPEN} — single probe call allowed; success
 *       closes the circuit, failure re-opens it.</li>
 * </ol>
 *
 * <p>This class is thread-safe via {@code synchronized} methods.
 * For time-controlled tests supply a custom {@code clock} instead of
 * {@link Instant#now}.
 */
public final class EndpointCircuitBreaker {

    private final String                endpointName;
    private final CircuitBreakerPolicy  policy;
    private final Supplier<Instant>     clock;

    private CircuitBreakerState state            = CircuitBreakerState.CLOSED;
    private int                 consecutiveFails = 0;
    private Instant             openedAt         = null;
    private long                totalCalls       = 0L;
    private long                totalFailures    = 0L;

    // -------------------------------------------------------------------------

    public EndpointCircuitBreaker(String endpointName, CircuitBreakerPolicy policy) {
        this(endpointName, policy, Instant::now);
    }

    /** Test constructor — accepts a custom clock for deterministic time control. */
    public EndpointCircuitBreaker(String endpointName,
                                   CircuitBreakerPolicy policy,
                                   Supplier<Instant> clock) {
        this.endpointName = endpointName;
        this.policy       = policy;
        this.clock        = clock;
    }

    // -------------------------------------------------------------------------
    // Call control
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if a call should be forwarded to the real method.
     * Returns {@code false} (or throws {@link CircuitBreakerOpenException}) when
     * the circuit is OPEN and the delay has not yet elapsed.
     *
     * @throws CircuitBreakerOpenException if the circuit is OPEN
     */
    public synchronized boolean isCallPermitted() {
        if (state == CircuitBreakerState.CLOSED) {
            return true;
        }
        if (state == CircuitBreakerState.OPEN) {
            if (delayElapsed()) {
                transitionTo(CircuitBreakerState.HALF_OPEN);
                return true;  // allow the probe call
            }
            throw new CircuitBreakerOpenException(endpointName);
        }
        // HALF_OPEN: probe call was already allowed; further calls are blocked until resolved
        throw new CircuitBreakerOpenException(endpointName);
    }

    /**
     * Records a successful call.
     * <ul>
     *   <li>In HALF_OPEN: resets the circuit to CLOSED.</li>
     *   <li>In CLOSED: resets the consecutive failure counter.</li>
     * </ul>
     */
    public synchronized void recordSuccess() {
        totalCalls++;
        consecutiveFails = 0;
        if (state == CircuitBreakerState.HALF_OPEN) {
            transitionTo(CircuitBreakerState.CLOSED);
        }
    }

    /**
     * Records a failed call.
     * <ul>
     *   <li>In CLOSED: increments consecutive failures; opens if threshold reached.</li>
     *   <li>In HALF_OPEN: probe failed — re-opens the circuit.</li>
     * </ul>
     */
    public synchronized void recordFailure() {
        totalCalls++;
        totalFailures++;
        consecutiveFails++;
        if (state == CircuitBreakerState.CLOSED
                && consecutiveFails >= policy.failureThreshold()) {
            transitionTo(CircuitBreakerState.OPEN);
        } else if (state == CircuitBreakerState.HALF_OPEN) {
            transitionTo(CircuitBreakerState.OPEN);
        }
    }

    // -------------------------------------------------------------------------
    // Introspection
    // -------------------------------------------------------------------------

    public synchronized CircuitBreakerState state()        { return state; }
    public synchronized int  consecutiveFailures()         { return consecutiveFails; }
    public synchronized long totalCalls()                  { return totalCalls; }
    public synchronized long totalFailures()               { return totalFailures; }
    public String endpointName()                           { return endpointName; }
    public CircuitBreakerPolicy policy()                   { return policy; }

    /** Resets to CLOSED with zeroed counters (test helper). */
    public synchronized void reset() {
        state            = CircuitBreakerState.CLOSED;
        consecutiveFails = 0;
        openedAt         = null;
        totalCalls       = 0L;
        totalFailures    = 0L;
    }

    // -------------------------------------------------------------------------

    private void transitionTo(CircuitBreakerState next) {
        state = next;
        if (next == CircuitBreakerState.OPEN) {
            openedAt         = clock.get();
            consecutiveFails = 0;
        } else if (next == CircuitBreakerState.CLOSED) {
            openedAt         = null;
            consecutiveFails = 0;
        }
    }

    private boolean delayElapsed() {
        if (openedAt == null) return true;
        return clock.get().isAfter(openedAt.plus(policy.delay()));
    }
}
