package io.gauss.augur.circuitbreaker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link EndpointCircuitBreaker} and {@link CircuitBreakerRegistry}.
 * Covers HU-053 acceptance criteria.
 */
class CircuitBreakerRegistryTest {

    private static final CircuitBreakerPolicy POLICY_5_30S =
            CircuitBreakerPolicy.of(5, "30s", "fallbackMethod");

    private AtomicReference<Instant> clockRef;
    private CircuitBreakerRegistry   registry;
    private EndpointCircuitBreaker   cb;

    @BeforeEach
    void setUp() {
        clockRef = new AtomicReference<>(Instant.now());
        registry = new CircuitBreakerRegistry(clockRef::get);
        cb       = registry.getOrCreate("churn-endpoint", POLICY_5_30S);
    }

    // -------------------------------------------------------------------------
    // CircuitBreakerPolicy — parsing
    // -------------------------------------------------------------------------

    @Test
    void policy_parsesSeconds() {
        assertThat(CircuitBreakerPolicy.of(3, "30s").delay())
                .isEqualTo(Duration.ofSeconds(30));
    }

    @Test
    void policy_parsesMinutes() {
        assertThat(CircuitBreakerPolicy.of(3, "2m").delay())
                .isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    void policy_invalidUnit_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> CircuitBreakerPolicy.of(3, "10x"));
    }

    // -------------------------------------------------------------------------
    // CLOSED state
    // -------------------------------------------------------------------------

    @Test
    void initialState_isClosed() {
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void closed_permitsCall() {
        assertThat(cb.isCallPermitted()).isTrue();
    }

    @Test
    void closed_successResetsConsecutiveFailures() {
        cb.recordFailure();
        cb.recordFailure();
        cb.recordSuccess();
        assertThat(cb.consecutiveFailures()).isZero();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void closed_belowThreshold_staysClosed() {
        for (int i = 0; i < 4; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    // -------------------------------------------------------------------------
    // Transition CLOSED → OPEN
    // -------------------------------------------------------------------------

    @Test
    void consecutiveFailures_atThreshold_opensCircuit() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void open_rejectsCall_throwsException() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        assertThatExceptionOfType(CircuitBreakerOpenException.class)
                .isThrownBy(cb::isCallPermitted)
                .withMessageContaining("churn-endpoint");
    }

    @Test
    void open_exception_containsEndpointName() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        try {
            cb.isCallPermitted();
            fail("Expected CircuitBreakerOpenException");
        } catch (CircuitBreakerOpenException e) {
            assertThat(e.endpointName()).isEqualTo("churn-endpoint");
        }
    }

    // -------------------------------------------------------------------------
    // Transition OPEN → HALF_OPEN
    // -------------------------------------------------------------------------

    @Test
    void open_afterDelay_movesToHalfOpen() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        // advance past 30s delay
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(31)));
        assertThat(cb.isCallPermitted()).isTrue();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.HALF_OPEN);
    }

    @Test
    void open_beforeDelay_remainsOpen() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(10)));
        assertThatExceptionOfType(CircuitBreakerOpenException.class)
                .isThrownBy(cb::isCallPermitted);
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    // -------------------------------------------------------------------------
    // Transition HALF_OPEN → CLOSED (probe succeeds)
    // -------------------------------------------------------------------------

    @Test
    void halfOpen_successCloses_circuit() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(31)));
        cb.isCallPermitted();                    // advance to HALF_OPEN
        cb.recordSuccess();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void halfOpen_success_resetsConsecutiveFailures() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(31)));
        cb.isCallPermitted();
        cb.recordSuccess();
        assertThat(cb.consecutiveFailures()).isZero();
    }

    // -------------------------------------------------------------------------
    // Transition HALF_OPEN → OPEN (probe fails)
    // -------------------------------------------------------------------------

    @Test
    void halfOpen_failureReopens_circuit() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(31)));
        cb.isCallPermitted();
        cb.recordFailure();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.OPEN);
    }

    @Test
    void halfOpen_secondCallBlocked_beforeProbeCompletes() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        clockRef.set(clockRef.get().plus(Duration.ofSeconds(31)));
        cb.isCallPermitted();   // transitions to HALF_OPEN
        // second call before probe result
        assertThatExceptionOfType(CircuitBreakerOpenException.class)
                .isThrownBy(cb::isCallPermitted);
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    @Test
    void totalCalls_tracked() {
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordSuccess();
        assertThat(cb.totalCalls()).isEqualTo(3);
    }

    @Test
    void totalFailures_tracked() {
        cb.recordSuccess();
        cb.recordFailure();
        cb.recordFailure();
        assertThat(cb.totalFailures()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Registry
    // -------------------------------------------------------------------------

    @Test
    void registry_getOrCreate_returnsSameInstance() {
        EndpointCircuitBreaker cb2 = registry.getOrCreate("churn-endpoint", POLICY_5_30S);
        assertThat(cb2).isSameAs(cb);
    }

    @Test
    void registry_differentEndpoints_isolatedState() {
        EndpointCircuitBreaker cb2 = registry.getOrCreate("risk-endpoint", POLICY_5_30S);
        for (int i = 0; i < 5; i++) cb.recordFailure();
        assertThat(cb2.state()).isEqualTo(CircuitBreakerState.CLOSED);
    }

    @Test
    void registry_find_returnsEmpty_whenAbsent() {
        assertThat(registry.find("nonexistent")).isEmpty();
    }

    @Test
    void registry_size_reflectsRegistrations() {
        registry.getOrCreate("ep-2", POLICY_5_30S);
        assertThat(registry.size()).isEqualTo(2); // churn + ep-2
    }

    @Test
    void reset_clearsCountersAndState() {
        for (int i = 0; i < 5; i++) cb.recordFailure();
        cb.reset();
        assertThat(cb.state()).isEqualTo(CircuitBreakerState.CLOSED);
        assertThat(cb.totalCalls()).isZero();
    }
}
