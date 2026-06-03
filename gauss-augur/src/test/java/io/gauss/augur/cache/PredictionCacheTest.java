package io.gauss.augur.cache;

import com.github.benmanes.caffeine.cache.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PredictionCache} and {@link CacheKey}.
 * Covers HU-021 acceptance criteria.
 */
class PredictionCacheTest {

    private AtomicLong  tickerNanos;
    private Ticker      ticker;
    private PredictionCache cache;

    @BeforeEach
    void setUp() {
        tickerNanos = new AtomicLong(0L);
        ticker      = tickerNanos::get;
        cache       = new PredictionCache(Duration.ofMinutes(5), ticker);
    }

    // -------------------------------------------------------------------------
    // CacheKey
    // -------------------------------------------------------------------------

    @Test
    void cacheKey_of_sameInputSameHash() {
        CacheKey k1 = CacheKey.of("churn", "customer-1");
        CacheKey k2 = CacheKey.of("churn", "customer-1");
        assertThat(k1).isEqualTo(k2);
        assertThat(k1.asString()).isEqualTo(k2.asString());
    }

    @Test
    void cacheKey_of_differentInput_differentHash() {
        CacheKey k1 = CacheKey.of("churn", "customer-1");
        CacheKey k2 = CacheKey.of("churn", "customer-2");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    void cacheKey_of_differentEndpoint_differentKey() {
        CacheKey k1 = CacheKey.of("churn",    "customer-1");
        CacheKey k2 = CacheKey.of("sentiment","customer-1");
        assertThat(k1).isNotEqualTo(k2);
    }

    @Test
    void cacheKey_asString_containsEndpointName() {
        CacheKey key = CacheKey.of("risk-model", "input");
        assertThat(key.asString()).contains("risk-model");
    }

    // -------------------------------------------------------------------------
    // Basic cache operations
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_onMiss() {
        assertThat(cache.get(CacheKey.of("ep", "input"))).isEmpty();
    }

    @Test
    void put_and_get_roundTrip() {
        CacheKey key = CacheKey.of("churn", "c-1");
        cache.put(key, 0.87);
        assertThat(cache.get(key)).hasValue(0.87);
    }

    @Test
    void put_overwritesPreviousValue() {
        CacheKey key = CacheKey.of("churn", "c-2");
        cache.put(key, 0.5);
        cache.put(key, 0.9);
        assertThat(cache.get(key)).hasValue(0.9);
    }

    @Test
    void evict_removesEntry() {
        CacheKey key = CacheKey.of("ep", "i-1");
        cache.put(key, "prediction");
        cache.evict(key);
        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void evict_noOp_whenKeyAbsent() {
        assertThatNoException().isThrownBy(
                () -> cache.evict(CacheKey.of("ep", "absent")));
    }

    @Test
    void clear_removesAllEntries() {
        cache.put(CacheKey.of("ep", "i-1"), "a");
        cache.put(CacheKey.of("ep", "i-2"), "b");
        cache.clear();
        assertThat(cache.get(CacheKey.of("ep", "i-1"))).isEmpty();
        assertThat(cache.get(CacheKey.of("ep", "i-2"))).isEmpty();
    }

    // -------------------------------------------------------------------------
    // TTL expiry via custom Ticker
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_afterTtlExpired() {
        CacheKey key = CacheKey.of("ep", "i-1");
        cache.put(key, "result");

        // advance ticker past 5 minutes TTL
        tickerNanos.set(Duration.ofMinutes(6).toNanos());

        assertThat(cache.get(key)).isEmpty();
    }

    @Test
    void get_returnsValue_beforeTtlExpires() {
        CacheKey key = CacheKey.of("ep", "i-1");
        cache.put(key, "result");

        tickerNanos.set(Duration.ofMinutes(4).toNanos());

        assertThat(cache.get(key)).hasValue("result");
    }

    // -------------------------------------------------------------------------
    // Hit / miss metrics
    // -------------------------------------------------------------------------

    @Test
    void missCount_incrementsOnCacheMiss() {
        cache.get(CacheKey.of("ep", "absent"));
        assertThat(cache.missCount()).isEqualTo(1);
    }

    @Test
    void hitCount_incrementsOnCacheHit() {
        CacheKey key = CacheKey.of("ep", "i-1");
        cache.put(key, "x");
        cache.get(key);
        assertThat(cache.hitCount()).isEqualTo(1);
    }

    @Test
    void hitCount_zero_beforeAnyAccess() {
        assertThat(cache.hitCount()).isZero();
        assertThat(cache.missCount()).isZero();
    }

    @Test
    void hitRatePercent_zero_whenNoAccesses() {
        assertThat(cache.hitRatePercent()).isZero();
    }

    @Test
    void hitRatePercent_100_whenAllHits() {
        CacheKey key = CacheKey.of("ep", "i");
        cache.put(key, "v");
        cache.get(key);
        cache.get(key);
        assertThat(cache.hitRatePercent()).isEqualTo(100L);
    }

    @Test
    void hitRatePercent_50_whenHalfHits() {
        CacheKey key = CacheKey.of("ep", "i");
        cache.put(key, "v");
        cache.get(key);                                  // hit
        cache.get(CacheKey.of("ep", "absent"));          // miss
        assertThat(cache.hitRatePercent()).isEqualTo(50L);
    }

    @Test
    void clear_resetsMetrics() {
        CacheKey key = CacheKey.of("ep", "i");
        cache.put(key, "v");
        cache.get(key);
        cache.get(CacheKey.of("ep", "absent"));
        cache.clear();
        assertThat(cache.hitCount()).isZero();
        assertThat(cache.missCount()).isZero();
    }
}
