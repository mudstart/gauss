package io.gauss.stratum.test;

import io.gauss.core.annotation.Feature;
import io.gauss.stratum.store.FeatureVector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GaussFeatureExtension}.
 * Covers HU-040 acceptance criteria for the JUnit 5 extension.
 */
@ExtendWith(GaussFeatureExtension.class)
class GaussFeatureExtensionTest {

    static class ItemFeatures {
        @Feature(ttl = "1h", description = "Item price")
        public double price(String itemId) { return itemId.length() * 1.5; }

        @Feature(ttl = "30m", description = "Stock level")
        public int stock(String itemId) { return 100; }
    }

    // -------------------------------------------------------------------------
    // Parameter injection
    // -------------------------------------------------------------------------

    @Test
    void extension_injectsInMemoryFeatureStore(InMemoryFeatureStore store) {
        assertThat(store).isNotNull();
    }

    @Test
    void extension_injectsTestClock(TestClock clock) {
        assertThat(clock).isNotNull();
    }

    @Test
    void extension_injectsBothParameters(InMemoryFeatureStore store, TestClock clock) {
        assertThat(store).isNotNull();
        assertThat(clock).isNotNull();
    }

    @Test
    void extension_clockIsTheSameAsStoreClock(InMemoryFeatureStore store, TestClock clock) {
        assertThat(store.clock()).isSameAs(clock);
    }

    // -------------------------------------------------------------------------
    // Isolation: fresh store and clock per test
    // -------------------------------------------------------------------------

    @Test
    void extension_storeIsEmpty_atTestStart(InMemoryFeatureStore store) {
        assertThat(store.size()).isZero();
    }

    @Test
    void extension_computeCount_zero_atTestStart(InMemoryFeatureStore store) {
        assertThat(store.computeCount("price")).isZero();
    }

    // -------------------------------------------------------------------------
    // TTL-driven expiry through injected clock
    // -------------------------------------------------------------------------

    @Test
    void injectedClock_advancesExpiry(InMemoryFeatureStore store, TestClock clock) {
        ItemFeatures bean = new ItemFeatures();
        store.getAll("item-1", bean, ItemFeatures.class);

        clock.advance(Duration.ofHours(2));  // exceed 1h TTL for price

        assertThat(store.get("item-1",
                io.gauss.stratum.feature.FeatureClass.scan(ItemFeatures.class)
                        .find("price").orElseThrow()))
                .isEmpty();
    }

    // -------------------------------------------------------------------------
    // Full feature store interaction via injected pair
    // -------------------------------------------------------------------------

    @Test
    void getAll_populatesStoreAndIncrementsComputeCount(InMemoryFeatureStore store) {
        ItemFeatures bean = new ItemFeatures();
        FeatureVector vector = store.getAll("item-42", bean, ItemFeatures.class);

        assertThat(vector.size()).isEqualTo(2);
        assertThat(vector.get("price")).isPresent();
        assertThat(vector.get("stock")).isPresent();
        assertThat(store.computeCount("price")).isEqualTo(1);
        assertThat(store.computeCount("stock")).isEqualTo(1);
    }

    @Test
    void secondGetAll_doesNotRecompute(InMemoryFeatureStore store) {
        ItemFeatures bean = new ItemFeatures();
        store.getAll("item-7", bean, ItemFeatures.class);
        store.getAll("item-7", bean, ItemFeatures.class);

        assertThat(store.computeCount("price")).isEqualTo(1);
        assertThat(store.computeCount("stock")).isEqualTo(1);
    }

    @Test
    void afterExpiry_recomputes(InMemoryFeatureStore store, TestClock clock) {
        ItemFeatures bean = new ItemFeatures();
        store.getAll("item-3", bean, ItemFeatures.class);

        clock.advance(Duration.ofHours(2));  // expire both features
        store.getAll("item-3", bean, ItemFeatures.class);

        assertThat(store.computeCount("price")).isEqualTo(2);
    }
}
