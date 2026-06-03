package io.gauss.augur.version;

import io.gauss.core.annotation.ModelVersion;
import io.gauss.core.annotation.ModelVersions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link VersionRouter} and {@link VersionWeight}.
 * Covers HU-019 acceptance criteria.
 */
class VersionRouterTest {

    // -------------------------------------------------------------------------
    // Fixture endpoint class
    // -------------------------------------------------------------------------

    @ModelVersion(value = "v1", weight = 80)
    @ModelVersion(value = "v2", weight = 20)
    static class ChurnEndpoint { }

    @ModelVersion(value = "only", weight = 100)
    static class SingleVersionEndpoint { }

    static class NoVersionEndpoint { }

    // -------------------------------------------------------------------------
    // VersionWeight validation
    // -------------------------------------------------------------------------

    @Test
    void versionWeight_negativeWeight_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VersionWeight("v1", -1));
    }

    @Test
    void versionWeight_zeroWeight_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VersionWeight("v1", 0));
    }

    // -------------------------------------------------------------------------
    // fromAnnotations factory
    // -------------------------------------------------------------------------

    @Test
    void fromAnnotations_parsesRepeatedAnnotations() {
        VersionRouter r = VersionRouter.fromAnnotations(ChurnEndpoint.class);
        assertThat(r.versionCount()).isEqualTo(2);
    }

    @Test
    void fromAnnotations_singleAnnotation() {
        VersionRouter r = VersionRouter.fromAnnotations(SingleVersionEndpoint.class);
        assertThat(r.versionCount()).isEqualTo(1);
    }

    @Test
    void fromAnnotations_noAnnotation_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VersionRouter.fromAnnotations(NoVersionEndpoint.class));
    }

    @Test
    void fromAnnotations_weightsCorrect() {
        VersionRouter r = VersionRouter.fromAnnotations(ChurnEndpoint.class);
        assertThat(r.currentWeights()).anyMatch(w -> w.version().equals("v1") && w.weight() == 80);
        assertThat(r.currentWeights()).anyMatch(w -> w.version().equals("v2") && w.weight() == 20);
    }

    // -------------------------------------------------------------------------
    // Route — 100 % single version
    // -------------------------------------------------------------------------

    @Test
    void route_singleVersion_alwaysReturnsThatVersion() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        for (int i = 0; i < 20; i++) {
            assertThat(r.route()).isEqualTo("v1");
        }
    }

    // -------------------------------------------------------------------------
    // Route — weighted distribution
    // -------------------------------------------------------------------------

    @Test
    void route_80_20_split_approximateDistribution() {
        VersionRouter r = new VersionRouter(
                List.of(new VersionWeight("v1", 80), new VersionWeight("v2", 20)),
                new Random(42));   // seeded for reproducibility

        long v1Count = IntStream.range(0, 1000)
                .mapToObj(i -> r.route())
                .filter("v1"::equals).count();

        // Expect ~800, allow ±100 tolerance
        assertThat(v1Count).isBetween(700L, 900L);
    }

    @Test
    void route_equalWeights_approximatelyBalanced() {
        VersionRouter r = new VersionRouter(
                List.of(new VersionWeight("a", 50), new VersionWeight("b", 50)),
                new Random(7));

        long aCount = IntStream.range(0, 1000)
                .mapToObj(i -> r.route())
                .filter("a"::equals).count();

        assertThat(aCount).isBetween(400L, 600L);
    }

    // -------------------------------------------------------------------------
    // Call counts
    // -------------------------------------------------------------------------

    @Test
    void callCount_incrementsOnEachRoute() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        r.route();
        r.route();
        r.route();
        assertThat(r.callCount("v1")).isEqualTo(3);
    }

    @Test
    void callCount_zeroBeforeAnyRoute() {
        VersionRouter r = VersionRouter.fromAnnotations(ChurnEndpoint.class);
        assertThat(r.callCount("v1")).isZero();
        assertThat(r.callCount("v2")).isZero();
    }

    @Test
    void callCount_unknownVersion_returnsZero() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        assertThat(r.callCount("nonexistent")).isZero();
    }

    // -------------------------------------------------------------------------
    // Runtime weight updates
    // -------------------------------------------------------------------------

    @Test
    void updateWeights_changesDistribution() {
        // Start 99% v1, 1% v2
        VersionRouter r = new VersionRouter(
                List.of(new VersionWeight("v1", 99), new VersionWeight("v2", 1)),
                new Random(1));

        // Flip to 100% v2 — all subsequent calls must return "v2"
        r.updateWeights(List.of(new VersionWeight("v2", 100)));

        for (int i = 0; i < 10; i++) {
            assertThat(r.route()).isEqualTo("v2");
        }
    }

    @Test
    void updateWeights_emptyList_throws() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> r.updateWeights(List.of()));
    }

    @Test
    void updateWeights_preservesExistingCallCounts() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        r.route();  // count v1 = 1
        r.updateWeights(List.of(new VersionWeight("v1", 50), new VersionWeight("v2", 50)));
        assertThat(r.callCount("v1")).isEqualTo(1);  // count preserved
    }

    // -------------------------------------------------------------------------
    // currentWeights
    // -------------------------------------------------------------------------

    @Test
    void currentWeights_returnsImmutableList() {
        VersionRouter r = new VersionRouter(List.of(new VersionWeight("v1", 100)));
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> r.currentWeights().add(new VersionWeight("v2", 50)));
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void emptyWeightsList_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new VersionRouter(List.of()));
    }
}
