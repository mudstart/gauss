package io.gauss.augur.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Ticker;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Caffeine-backed, TTL-scoped cache for ML prediction results (Augur module, HU-021).
 *
 * <p>Predictions are stored keyed by {@link CacheKey} (endpoint name + input hash).
 * Serving a cached result avoids re-invoking the model for identical inputs, reducing
 * latency and compute cost.
 *
 * <p>Hit and miss counters are maintained separately so callers can report the
 * cache effectiveness as a Micrometer metric.
 *
 * <p>Usage (programmatic):
 * <pre>{@code
 * PredictionCache cache = new PredictionCache(Duration.ofMinutes(5));
 *
 * CacheKey key = CacheKey.of("churn", input);
 * Optional<Object> hit = cache.get(key);
 * if (hit.isEmpty()) {
 *     Object result = model.predict(input);
 *     cache.put(key, result);
 * }
 * }</pre>
 *
 * <p>For time-controlled tests, use the {@link #PredictionCache(Duration, Ticker)}
 * constructor and supply a custom {@link Ticker}.
 */
public final class PredictionCache {

    private static final long   DEFAULT_MAX_SIZE = 10_000L;

    private final Cache<String, Object> cache;
    private final AtomicLong hitCount  = new AtomicLong();
    private final AtomicLong missCount = new AtomicLong();

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Creates a cache with the given TTL and the system wall-clock ticker.
     *
     * @param ttl time-to-live per entry (positive)
     */
    public PredictionCache(Duration ttl) {
        this(ttl, Ticker.systemTicker());
    }

    /**
     * Creates a cache with the given TTL and a custom {@link Ticker}.
     * Use a mutable ticker (e.g., backed by an {@code AtomicLong}) to control
     * time in unit tests.
     *
     * @param ttl    time-to-live per entry
     * @param ticker time source used by Caffeine for expiry
     */
    public PredictionCache(Duration ttl, Ticker ticker) {
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(ttl.toNanos(), TimeUnit.NANOSECONDS)
                .ticker(ticker)
                .maximumSize(DEFAULT_MAX_SIZE)
                .build();
    }

    // -------------------------------------------------------------------------
    // Cache operations
    // -------------------------------------------------------------------------

    /**
     * Returns the cached prediction for {@code key}, or {@link Optional#empty()}
     * on a miss.  Updates hit/miss counters accordingly.
     *
     * @param key the composite cache key
     * @return the cached value, or empty
     */
    public Optional<Object> get(CacheKey key) {
        Object value = cache.getIfPresent(key.asString());
        if (value == null) {
            missCount.incrementAndGet();
            return Optional.empty();
        }
        hitCount.incrementAndGet();
        return Optional.of(value);
    }

    /**
     * Stores a prediction result.  Any previously cached value for the same
     * key is overwritten.
     *
     * @param key   the composite cache key
     * @param value the prediction result to cache (must not be {@code null})
     */
    public void put(CacheKey key, Object value) {
        cache.put(key.asString(), value);
    }

    /**
     * Removes the entry for {@code key} from the cache.
     * No-op if the key is not present.
     */
    public void evict(CacheKey key) {
        cache.invalidate(key.asString());
    }

    /**
     * Removes all entries and resets hit/miss counters.
     */
    public void clear() {
        cache.invalidateAll();
        hitCount.set(0);
        missCount.set(0);
    }

    // -------------------------------------------------------------------------
    // Metrics
    // -------------------------------------------------------------------------

    /** Total number of cache hits since construction or last {@link #clear()}. */
    public long hitCount() {
        return hitCount.get();
    }

    /** Total number of cache misses since construction or last {@link #clear()}. */
    public long missCount() {
        return missCount.get();
    }

    /**
     * Integer percentage hit rate (0–100) based on all recorded accesses.
     * Returns 0 if no accesses have been made yet.
     */
    public long hitRatePercent() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0L : 100L * hitCount.get() / total;
    }
}
