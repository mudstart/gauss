package io.gauss.stratum.store;

import io.gauss.stratum.feature.FeatureDescriptor;

import java.time.Instant;
import java.util.Optional;

/**
 * SPI for the Gauss feature store (HU-027 / HU-029).
 *
 * <p>Implementations must be thread-safe.  The framework ships two built-in
 * implementations:
 * <ul>
 *   <li>{@link OnlineFeatureStore} — Caffeine-backed, targeting &lt;10 ms reads.</li>
 *   <li>{@link io.gauss.stratum.test.InMemoryFeatureStore} — for unit tests.</li>
 * </ul>
 */
public interface FeatureStore {

    /**
     * Returns the cached value for {@code entityId} / {@code feature}, or empty if
     * not cached (or expired).
     */
    Optional<Object> get(String entityId, FeatureDescriptor feature);

    /**
     * Stores a computed feature value.
     *
     * @param entityId  the entity identifier
     * @param feature   the feature descriptor
     * @param value     the computed value
     * @param expiresAt wall-clock time after which the value is considered stale
     */
    void put(String entityId, FeatureDescriptor feature, Object value, Instant expiresAt);

    /**
     * Returns {@code true} if a non-expired value is present for the given key.
     */
    boolean contains(String entityId, FeatureDescriptor feature);

    /**
     * Removes the cached value for the given key (forces recomputation on next access).
     */
    void evict(String entityId, FeatureDescriptor feature);

    /**
     * Removes all cached values from this store.
     */
    void clear();
}
