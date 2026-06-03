package io.gauss.stratum.store;

import io.gauss.core.annotation.Feature;
import io.gauss.stratum.feature.FeatureClass;
import io.gauss.stratum.feature.FeatureDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OnlineFeatureStore}.
 * Covers HU-029 acceptance criteria (cache hit/miss, getAll, metrics).
 */
class OnlineFeatureStoreTest {

    // -------------------------------------------------------------------------
    // Fixture feature class
    // -------------------------------------------------------------------------

    static class CustomerFeatures {
        @Feature(ttl = "1h", description = "Transaction count")
        public int txCount(String customerId) { return customerId.length(); }

        @Feature(ttl = "30m", description = "Risk score")
        public double riskScore(String customerId) { return 0.75; }
    }

    private OnlineFeatureStore store;
    private CustomerFeatures   bean;
    private FeatureClass       fc;
    private FeatureDescriptor  txCountDesc;

    @BeforeEach
    void setUp() {
        store       = new OnlineFeatureStore();
        bean        = new CustomerFeatures();
        fc          = FeatureClass.scan(CustomerFeatures.class);
        txCountDesc = fc.find("txCount").orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Cache miss / hit
    // -------------------------------------------------------------------------

    @Test
    void get_returnEmpty_onCacheMiss() {
        assertThat(store.get("c-1", txCountDesc)).isEmpty();
    }

    @Test
    void put_and_get_roundTrips() {
        store.put("c-1", txCountDesc, 42, Instant.now().plus(Duration.ofHours(1)));
        assertThat(store.get("c-1", txCountDesc)).hasValue(42);
    }

    @Test
    void contains_returnsFalse_beforePut() {
        assertThat(store.contains("c-1", txCountDesc)).isFalse();
    }

    @Test
    void contains_returnsTrue_afterPut() {
        store.put("c-1", txCountDesc, 42, Instant.now().plus(Duration.ofHours(1)));
        assertThat(store.contains("c-1", txCountDesc)).isTrue();
    }

    @Test
    void evict_removesEntry() {
        store.put("c-1", txCountDesc, 42, Instant.now().plus(Duration.ofHours(1)));
        store.evict("c-1", txCountDesc);
        assertThat(store.get("c-1", txCountDesc)).isEmpty();
    }

    @Test
    void clear_removesAllEntries() {
        FeatureDescriptor riskDesc = fc.find("riskScore").orElseThrow();
        store.put("c-1", txCountDesc, 42, Instant.now().plus(Duration.ofHours(1)));
        store.put("c-2", riskDesc,   0.5, Instant.now().plus(Duration.ofMinutes(30)));
        store.clear();
        assertThat(store.get("c-1", txCountDesc)).isEmpty();
        assertThat(store.get("c-2", riskDesc)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------------

    @Test
    void getAll_computesAllFeatures() {
        FeatureVector vector = store.getAll("customer-5", bean, CustomerFeatures.class);
        assertThat(vector.size()).isEqualTo(2);
        assertThat(vector.get("txCount")).isPresent();
        assertThat(vector.get("riskScore")).isPresent();
    }

    @Test
    void getAll_cachesResults_secondCallUsesCache() {
        store.getAll("c-1", bean, CustomerFeatures.class);
        store.getAll("c-1", bean, CustomerFeatures.class);
        // After first call, miss count stays at 2 (one per feature), hits at 2
        assertThat(store.hitCount("txCount")).isEqualTo(1);
        assertThat(store.hitCount("riskScore")).isEqualTo(1);
    }

    @Test
    void getAll_entityId_setOnVector() {
        FeatureVector vector = store.getAll("cust-99", bean, CustomerFeatures.class);
        assertThat(vector.entityId()).isEqualTo("cust-99");
    }

    // -------------------------------------------------------------------------
    // Cache metrics (HU-029)
    // -------------------------------------------------------------------------

    @Test
    void missCount_incrementsOnCacheMiss() {
        store.get("c-1", txCountDesc);
        assertThat(store.missCount("txCount")).isEqualTo(1);
    }

    @Test
    void hitCount_incrementsOnCacheHit() {
        store.put("c-1", txCountDesc, 42, Instant.now().plus(Duration.ofHours(1)));
        store.get("c-1", txCountDesc);
        assertThat(store.hitCount("txCount")).isEqualTo(1);
    }
}
