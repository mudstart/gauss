package io.gauss.stratum.test;

import io.gauss.core.annotation.Feature;
import io.gauss.stratum.feature.FeatureClass;
import io.gauss.stratum.feature.FeatureDescriptor;
import io.gauss.stratum.store.FeatureVector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link InMemoryFeatureStore} and {@link TestClock}.
 * Covers HU-040 acceptance criteria.
 */
class InMemoryFeatureStoreTest {

    static class SampleFeatures {
        @Feature(ttl = "1h", description = "Score")
        public double score(String id) { return id.length() * 0.1; }

        @Feature(ttl = "30m", description = "Rank")
        public int rank(String id) { return id.hashCode() % 100; }
    }

    private TestClock            clock;
    private InMemoryFeatureStore store;
    private FeatureClass         fc;
    private FeatureDescriptor    scoreDesc;
    private FeatureDescriptor    rankDesc;

    @BeforeEach
    void setUp() {
        clock     = new TestClock();
        store     = new InMemoryFeatureStore(clock);
        fc        = FeatureClass.scan(SampleFeatures.class);
        scoreDesc = fc.find("score").orElseThrow();
        rankDesc  = fc.find("rank").orElseThrow();
    }

    // -------------------------------------------------------------------------
    // Basic cache operations
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_onMiss() {
        assertThat(store.get("e-1", scoreDesc)).isEmpty();
    }

    @Test
    void put_and_get_roundTrips() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        assertThat(store.get("e-1", scoreDesc)).hasValue(0.9);
    }

    @Test
    void contains_trueAfterPut() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        assertThat(store.contains("e-1", scoreDesc)).isTrue();
    }

    @Test
    void evict_removesEntry() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        store.evict("e-1", scoreDesc);
        assertThat(store.get("e-1", scoreDesc)).isEmpty();
    }

    @Test
    void clear_removesAllEntries() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        store.put("e-2", rankDesc,  5,   clock.now().plus(Duration.ofMinutes(30)));
        store.clear();
        assertThat(store.get("e-1", scoreDesc)).isEmpty();
        assertThat(store.get("e-2", rankDesc)).isEmpty();
        assertThat(store.computeCount("score")).isZero();
    }

    // -------------------------------------------------------------------------
    // TTL expiry via TestClock
    // -------------------------------------------------------------------------

    @Test
    void get_returnsEmpty_afterTtlExpired() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        clock.advance(Duration.ofHours(2));  // advance past TTL
        assertThat(store.get("e-1", scoreDesc)).isEmpty();
    }

    @Test
    void get_returnsValue_beforeTtlExpires() {
        store.put("e-1", scoreDesc, 0.9, clock.now().plus(Duration.ofHours(1)));
        clock.advance(Duration.ofMinutes(30));  // still within TTL
        assertThat(store.get("e-1", scoreDesc)).hasValue(0.9);
    }

    @Test
    void contains_returnsFalse_afterExpiry() {
        store.put("e-1", scoreDesc, 0.5, clock.now().plus(Duration.ofSeconds(10)));
        clock.advance(Duration.ofSeconds(20));
        assertThat(store.contains("e-1", scoreDesc)).isFalse();
    }

    // -------------------------------------------------------------------------
    // Compute count tracking
    // -------------------------------------------------------------------------

    @Test
    void computeCount_zero_beforeAnyComputation() {
        assertThat(store.computeCount("score")).isZero();
    }

    @Test
    void computeCount_incrementsOnCacheMiss() {
        SampleFeatures bean = new SampleFeatures();
        store.getAll("e-1", bean, SampleFeatures.class);
        assertThat(store.computeCount("score")).isEqualTo(1);
        assertThat(store.computeCount("rank")).isEqualTo(1);
    }

    @Test
    void computeCount_doesNotIncrement_onCacheHit() {
        SampleFeatures bean = new SampleFeatures();
        store.getAll("e-1", bean, SampleFeatures.class);
        store.getAll("e-1", bean, SampleFeatures.class);  // second call — cache hit
        assertThat(store.computeCount("score")).isEqualTo(1);
    }

    @Test
    void computeCount_incrementsAgain_afterTtlExpiry() {
        SampleFeatures bean = new SampleFeatures();
        store.getAll("e-1", bean, SampleFeatures.class);
        clock.advance(Duration.ofHours(2));  // expire all features
        store.getAll("e-1", bean, SampleFeatures.class);
        assertThat(store.computeCount("score")).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // getAll
    // -------------------------------------------------------------------------

    @Test
    void getAll_returnsAllFeatures() {
        SampleFeatures bean = new SampleFeatures();
        FeatureVector vector = store.getAll("e-99", bean, SampleFeatures.class);
        assertThat(vector.size()).isEqualTo(2);
        assertThat(vector.get("score")).isPresent();
        assertThat(vector.get("rank")).isPresent();
    }

    @Test
    void getAll_entityIdSetOnVector() {
        SampleFeatures bean = new SampleFeatures();
        assertThat(store.getAll("e-77", bean, SampleFeatures.class).entityId())
                .isEqualTo("e-77");
    }

    // -------------------------------------------------------------------------
    // TestClock
    // -------------------------------------------------------------------------

    @Test
    void testClock_advance_increasesCurrentTime() {
        var before = clock.now();
        clock.advance(Duration.ofHours(3));
        assertThat(clock.now()).isAfter(before);
        assertThat(Duration.between(before, clock.now()).toHours()).isEqualTo(3);
    }

    @Test
    void testClock_advance_negative_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> clock.advance(Duration.ofSeconds(-1)));
    }

    @Test
    void testClock_reset_changesTime() {
        var fixed = clock.now().plus(Duration.ofDays(365));
        clock.reset(fixed);
        assertThat(clock.now()).isEqualTo(fixed);
    }
}
