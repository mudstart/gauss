package io.gauss.augur.circuitbreaker;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.time.Instant;

/**
 * Registry that manages one {@link EndpointCircuitBreaker} per endpoint (HU-053).
 *
 * <p>Circuit breakers are created on first access and cached for the lifetime of
 * the registry.  Each endpoint uses its own independent policy and state.
 *
 * <p>Usage:
 * <pre>{@code
 * CircuitBreakerRegistry registry = new CircuitBreakerRegistry();
 * CircuitBreakerPolicy   policy   = CircuitBreakerPolicy.of(5, "30s");
 *
 * EndpointCircuitBreaker cb = registry.getOrCreate("churn-endpoint", policy);
 * try {
 *     cb.isCallPermitted();
 *     Object result = model.predict(input);
 *     cb.recordSuccess();
 *     return result;
 * } catch (CircuitBreakerOpenException e) {
 *     return fallback(input);
 * } catch (Exception e) {
 *     cb.recordFailure();
 *     throw e;
 * }
 * }</pre>
 */
public final class CircuitBreakerRegistry {

    private final ConcurrentHashMap<String, EndpointCircuitBreaker> breakers =
            new ConcurrentHashMap<>();

    private final Supplier<Instant> clock;

    public CircuitBreakerRegistry() {
        this(Instant::now);
    }

    /** Test constructor — injects a custom clock into every circuit breaker created. */
    public CircuitBreakerRegistry(Supplier<Instant> clock) {
        this.clock = clock;
    }

    // -------------------------------------------------------------------------

    /**
     * Returns the circuit breaker for {@code endpointName}, creating one with
     * {@code policy} if it does not yet exist.
     */
    public EndpointCircuitBreaker getOrCreate(String endpointName, CircuitBreakerPolicy policy) {
        return breakers.computeIfAbsent(
                endpointName,
                name -> new EndpointCircuitBreaker(name, policy, clock));
    }

    /**
     * Returns the circuit breaker for {@code endpointName} if it has already
     * been created, or empty otherwise.
     */
    public Optional<EndpointCircuitBreaker> find(String endpointName) {
        return Optional.ofNullable(breakers.get(endpointName));
    }

    /** Returns all registered circuit breakers. */
    public Collection<EndpointCircuitBreaker> all() {
        return breakers.values();
    }

    /** Number of registered circuit breakers. */
    public int size() {
        return breakers.size();
    }

    /** Removes all circuit breakers (intended for tests). */
    public void clear() {
        breakers.clear();
    }
}
