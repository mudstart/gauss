package io.gauss.stratum.test;

import io.gauss.stratum.feature.FeatureClass;
import io.gauss.stratum.feature.FeatureDescriptor;
import io.gauss.stratum.feature.FeatureEvaluator;
import io.gauss.stratum.store.FeatureStore;
import io.gauss.stratum.store.FeatureVector;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory {@link FeatureStore} implementation for unit tests (HU-040).
 *
 * <p>Supports:
 * <ul>
 *   <li>TTL expiry driven by a {@link TestClock} — advance the clock to simulate expiration.</li>
 *   <li>Per-feature computation count — verify that caching is working correctly.</li>
 *   <li>Full {@link FeatureStore} contract — same interface as the production store.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * TestClock clock = new TestClock();
 * InMemoryFeatureStore store = new InMemoryFeatureStore(clock);
 *
 * // Pre-load a value
 * store.put(entityId, featureDesc, 42, clock.now().plus(Duration.ofHours(1)));
 *
 * // Simulate TTL expiry
 * clock.advance(Duration.ofHours(2));
 * assertThat(store.get(entityId, featureDesc)).isEmpty();
 *
 * // Check computation counts
 * assertThat(store.computeCount("myFeature")).isEqualTo(1);
 * }</pre>
 */
public final class InMemoryFeatureStore implements FeatureStore {

    private final TestClock                       clock;
    private final Map<String, CachedEntry>        store         = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger>      computeCounts = new ConcurrentHashMap<>();
    private final FeatureEvaluator                evaluator     = new FeatureEvaluator();

    private record CachedEntry(Object value, Instant expiresAt) {
        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }

    // -------------------------------------------------------------------------

    /**
     * Creates a store backed by a fresh {@link TestClock} starting at {@link Instant#now()}.
     */
    public InMemoryFeatureStore() {
        this(new TestClock());
    }

    /**
     * Creates a store backed by the given clock.
     */
    public InMemoryFeatureStore(TestClock clock) {
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // FeatureStore contract
    // -------------------------------------------------------------------------

    @Override
    public Optional<Object> get(String entityId, FeatureDescriptor feature) {
        CachedEntry entry = store.get(feature.cacheKey(entityId));
        if (entry == null || entry.isExpired(clock.now())) {
            return Optional.empty();
        }
        return Optional.ofNullable(entry.value());
    }

    @Override
    public void put(String entityId, FeatureDescriptor feature, Object value, Instant expiresAt) {
        store.put(feature.cacheKey(entityId), new CachedEntry(value, expiresAt));
    }

    @Override
    public boolean contains(String entityId, FeatureDescriptor feature) {
        return get(entityId, feature).isPresent();
    }

    @Override
    public void evict(String entityId, FeatureDescriptor feature) {
        store.remove(feature.cacheKey(entityId));
    }

    @Override
    public void clear() {
        store.clear();
        computeCounts.clear();
    }

    // -------------------------------------------------------------------------
    // High-level getAll (mirrors OnlineFeatureStore)
    // -------------------------------------------------------------------------

    /**
     * Returns all features for {@code entityId}, computing and caching any that
     * are missing or expired.
     *
     * <p>Computation counts are incremented for each feature that is computed
     * (not served from cache).
     */
    public FeatureVector getAll(String entityId, Object featureBean, Class<?> featureClass) {
        FeatureClass fc = FeatureClass.scan(featureClass);
        Map<String, Object> results = new LinkedHashMap<>();

        for (FeatureDescriptor desc : fc.topologicalOrder()) {
            Object value = get(entityId, desc).orElseGet(() -> {
                Object computed = evaluator.evaluate(
                        featureBean, desc, entityId, fc,
                        (dep, eid) -> Optional.ofNullable(results.get(dep.name())));
                computeCounts.computeIfAbsent(desc.name(), k -> new AtomicInteger())
                             .incrementAndGet();
                Instant expiresAt = clock.now().plus(desc.ttl());
                put(entityId, desc, computed, expiresAt);
                return computed;
            });
            results.put(desc.name(), value);
        }
        return FeatureVector.of(entityId, results);
    }

    // -------------------------------------------------------------------------
    // Test introspection
    // -------------------------------------------------------------------------

    /**
     * Returns the number of times the named feature was computed (cache-miss
     * evaluations) since this store was created or last {@link #clear()}ed.
     */
    public int computeCount(String featureName) {
        AtomicInteger c = computeCounts.get(featureName);
        return c == null ? 0 : c.get();
    }

    /** Returns the backing {@link TestClock}. */
    public TestClock clock() {
        return clock;
    }

    /** Returns the total number of entries currently in the store (including potentially expired). */
    public int size() {
        return store.size();
    }
}
