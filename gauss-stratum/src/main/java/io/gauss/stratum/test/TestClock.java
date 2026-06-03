package io.gauss.stratum.test;

import java.time.Duration;
import java.time.Instant;

/**
 * A manually-advanceable clock for TTL simulation in feature store tests (HU-040).
 *
 * <p>Usage:
 * <pre>{@code
 * TestClock clock = new TestClock();
 * InMemoryFeatureStore store = new InMemoryFeatureStore(clock);
 *
 * store.put(entityId, feature, value, clock.now().plus(feature.ttl()));
 *
 * // Advance past the TTL to simulate expiry
 * clock.advance(Duration.ofHours(2));
 *
 * assertThat(store.get(entityId, feature)).isEmpty();  // expired
 * }</pre>
 */
public final class TestClock {

    private Instant current;

    /**
     * Creates a clock starting at {@link Instant#now()} at construction time.
     */
    public TestClock() {
        this.current = Instant.now();
    }

    /**
     * Creates a clock starting at the given instant.
     */
    public TestClock(Instant start) {
        this.current = start;
    }

    // -------------------------------------------------------------------------

    /**
     * Returns the current simulated instant.
     */
    public Instant now() {
        return current;
    }

    /**
     * Advances the clock by the given duration.
     *
     * @param duration must be non-negative
     * @throws IllegalArgumentException if {@code duration} is negative
     */
    public void advance(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("Duration must not be negative: " + duration);
        }
        current = current.plus(duration);
    }

    /**
     * Resets the clock to the given instant.
     */
    public void reset(Instant newInstant) {
        this.current = newInstant;
    }

    /**
     * Resets the clock to the real current wall-clock time.
     */
    public void resetToNow() {
        this.current = Instant.now();
    }
}
