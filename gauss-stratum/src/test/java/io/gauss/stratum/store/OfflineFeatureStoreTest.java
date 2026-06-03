package io.gauss.stratum.store;

import io.gauss.core.annotation.Feature;
import io.gauss.stratum.test.InMemoryFeatureStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link OfflineFeatureStore}.
 * Covers HU-028 acceptance criteria (materialization, incremental semantics).
 */
class OfflineFeatureStoreTest {

    static class ProductFeatures {
        @Feature(ttl = "24h", description = "View count")
        public int viewCount(String productId) { return productId.length() * 3; }

        @Feature(ttl = "12h", description = "Purchase rate")
        public double purchaseRate(String productId) { return 0.05; }
    }

    private InMemoryFeatureStore targetStore;
    private OfflineFeatureStore  offline;

    @BeforeEach
    void setUp() {
        targetStore = new InMemoryFeatureStore();
        offline     = new OfflineFeatureStore(targetStore, new ProductFeatures());
    }

    // -------------------------------------------------------------------------
    // Basic materialization
    // -------------------------------------------------------------------------

    @Test
    void materialize_computesAllFeaturesForAllEntities() {
        List<String> entities = List.of("p-1", "p-2", "p-3");
        MaterializationResult result = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, entities);

        assertThat(result.entitiesTotal()).isEqualTo(3);
        assertThat(result.featuresComputed()).isEqualTo(6); // 3 entities x 2 features
        assertThat(result.featuresSkipped()).isEqualTo(0);
    }

    @Test
    void materialize_storesValuesInTargetStore() {
        offline.materialize("2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-42"));

        FeatureVector vector = targetStore.getAll("p-42", new ProductFeatures(), ProductFeatures.class);
        assertThat(vector.get("viewCount")).isPresent();
        assertThat(vector.get("purchaseRate")).isPresent();
    }

    @Test
    void materialize_reportsCorrectFeatureClass() {
        MaterializationResult result = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));
        assertThat(result.featureClass()).isEqualTo(ProductFeatures.class);
    }

    @Test
    void materialize_reportsDateRange() {
        MaterializationResult result = offline.materialize(
                "2024-01-01", "2024-06-30", ProductFeatures.class, List.of("p-1"));
        assertThat(result.fromDate()).isEqualTo("2024-01-01");
        assertThat(result.toDate()).isEqualTo("2024-06-30");
    }

    // -------------------------------------------------------------------------
    // Incremental semantics
    // -------------------------------------------------------------------------

    @Test
    void materialize_skipsAlreadyCachedValues() {
        // First materialization
        offline.materialize("2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));

        // Second materialization — should skip everything
        MaterializationResult second = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));

        assertThat(second.featuresSkipped()).isEqualTo(2);
        assertThat(second.featuresComputed()).isEqualTo(0);
    }

    @Test
    void materialize_incrementallyAddsNewEntities() {
        offline.materialize("2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));

        MaterializationResult second = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1", "p-2"));

        // p-1: 2 skipped, p-2: 2 computed
        assertThat(second.featuresSkipped()).isEqualTo(2);
        assertThat(second.featuresComputed()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Duration and statistics
    // -------------------------------------------------------------------------

    @Test
    void materialize_durationIsNonNegative() {
        MaterializationResult result = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));
        assertThat(result.duration()).isGreaterThanOrEqualTo(java.time.Duration.ZERO);
    }

    @Test
    void materialize_totalAttempted_isSumOfComputedAndSkipped() {
        offline.materialize("2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1"));
        MaterializationResult second = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of("p-1", "p-2"));
        assertThat(second.totalAttempted())
                .isEqualTo(second.featuresComputed() + second.featuresSkipped());
    }

    @Test
    void materialize_emptyEntityList_zeroStats() {
        MaterializationResult result = offline.materialize(
                "2024-01-01", "2024-12-31", ProductFeatures.class, List.of());
        assertThat(result.entitiesTotal()).isZero();
        assertThat(result.featuresComputed()).isZero();
    }
}
