package io.gauss.stratum.store;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.gauss.stratum.feature.FeatureClass;
import io.gauss.stratum.feature.FeatureDescriptor;
import io.gauss.stratum.feature.FeatureEvaluator;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Caffeine-backed online feature store targeting &lt;10 ms read latency (HU-029).
 *
 * <p>On a cache miss the feature is computed synchronously from the provided feature
 * bean via {@link FeatureEvaluator}, then inserted with the TTL declared in
 * {@link io.gauss.core.annotation.Feature#ttl()}.
 *
 * <p>Hit/miss metrics are tracked per feature and retrievable via
 * {@link #hitCount(String)} and {@link #missCount(String)}.
 *
 * <p>Usage:
 * <pre>{@code
 * OnlineFeatureStore store = new OnlineFeatureStore(myFeaturesBean, MyFeatures.class);
 * FeatureVector vector = store.getAll("customer-42", myFeaturesBean, MyFeatures.class);
 * }</pre>
 */
public final class OnlineFeatureStore implements FeatureStore {

    private static final Logger LOG = Logger.getLogger(OnlineFeatureStore.class.getName());

    // Caffeine cache: key = "entityId:featureName:version", value = CachedEntry
    private final Cache<String, CachedEntry> cache;
    private final FeatureEvaluator           evaluator = new FeatureEvaluator();

    private final Map<String, AtomicLong> hits   = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, AtomicLong> misses = new java.util.concurrent.ConcurrentHashMap<>();

    // -------------------------------------------------------------------------

    /** Wrapper that carries the value together with its expiry instant. */
    private record CachedEntry(Object value, Instant expiresAt) {}

    // -------------------------------------------------------------------------

    public OnlineFeatureStore() {
        this.cache = Caffeine.newBuilder()
                .expireAfter(new Expiry<String, CachedEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CachedEntry entry, long currentTime) {
                        java.time.Duration remaining =
                                java.time.Duration.between(Instant.now(), entry.expiresAt());
                        return Math.max(0L, remaining.toNanos());
                    }
                    @Override
                    public long expireAfterUpdate(String key, CachedEntry entry,
                                                   long currentTime, long currentDuration) {
                        return expireAfterCreate(key, entry, currentTime);
                    }
                    @Override
                    public long expireAfterRead(String key, CachedEntry entry,
                                                 long currentTime, long currentDuration) {
                        return currentDuration;  // read does not reset TTL
                    }
                })
                .build();
    }

    // -------------------------------------------------------------------------
    // FeatureStore implementation
    // -------------------------------------------------------------------------

    @Override
    public Optional<Object> get(String entityId, FeatureDescriptor feature) {
        String key    = feature.cacheKey(entityId);
        CachedEntry e = cache.getIfPresent(key);
        if (e != null) {
            hits.computeIfAbsent(feature.name(), k -> new AtomicLong()).incrementAndGet();
            return Optional.ofNullable(e.value());
        }
        misses.computeIfAbsent(feature.name(), k -> new AtomicLong()).incrementAndGet();
        return Optional.empty();
    }

    @Override
    public void put(String entityId, FeatureDescriptor feature, Object value, Instant expiresAt) {
        cache.put(feature.cacheKey(entityId), new CachedEntry(value, expiresAt));
    }

    @Override
    public boolean contains(String entityId, FeatureDescriptor feature) {
        return cache.getIfPresent(feature.cacheKey(entityId)) != null;
    }

    @Override
    public void evict(String entityId, FeatureDescriptor feature) {
        cache.invalidate(feature.cacheKey(entityId));
    }

    @Override
    public void clear() {
        cache.invalidateAll();
    }

    // -------------------------------------------------------------------------
    // High-level getAll (HU-029)
    // -------------------------------------------------------------------------

    /**
     * Returns all features for {@code entityId}, computing any that are not cached.
     *
     * <p>Features are evaluated in dependency-topological order so that
     * dependencies are available when dependents are computed.
     *
     * @param entityId     entity identifier
     * @param featureBean  bean instance whose {@code @Feature} methods are called
     * @param featureClass the class that declares the features
     * @return feature vector with all feature values
     */
    public FeatureVector getAll(String entityId, Object featureBean, Class<?> featureClass) {
        FeatureClass fc = FeatureClass.scan(featureClass);
        Map<String, Object> results = new LinkedHashMap<>();

        for (FeatureDescriptor desc : fc.topologicalOrder()) {
            Object value = get(entityId, desc).orElseGet(() -> {
                Object computed = evaluator.evaluate(
                        featureBean, desc, entityId, fc,
                        (dep, eid) -> Optional.ofNullable(results.get(dep.name())));
                Instant expiresAt = Instant.now().plus(desc.ttl());
                put(entityId, desc, computed, expiresAt);
                LOG.fine(() -> "Stratum: computed feature '" + desc.name()
                        + "' for entity '" + entityId + "'");
                return computed;
            });
            results.put(desc.name(), value);
        }
        return FeatureVector.of(entityId, results);
    }

    // -------------------------------------------------------------------------
    // Metrics (HU-029)
    // -------------------------------------------------------------------------

    /** Returns the cache hit count for the named feature. */
    public long hitCount(String featureName) {
        return hits.getOrDefault(featureName, new AtomicLong()).get();
    }

    /** Returns the cache miss count for the named feature. */
    public long missCount(String featureName) {
        return misses.getOrDefault(featureName, new AtomicLong()).get();
    }
}
