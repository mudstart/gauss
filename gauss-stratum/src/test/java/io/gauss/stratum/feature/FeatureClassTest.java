package io.gauss.stratum.feature;

import io.gauss.core.annotation.Feature;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FeatureClass} and {@link TtlParser}.
 * Covers HU-027 acceptance criteria.
 */
class FeatureClassTest {

    // -------------------------------------------------------------------------
    // Fixture feature classes
    // -------------------------------------------------------------------------

    static class SimpleFeatures {
        @Feature(ttl = "1h", description = "Transaction count")
        public int txCount(String customerId) { return 42; }

        @Feature(ttl = "30m", description = "Average basket size", version = 2)
        public double avgBasket(String customerId) { return 12.5; }
    }

    static class DependentFeatures {
        @Feature(ttl = "1h", description = "Raw count")
        public int rawCount(String entityId) { return 10; }

        // depends on rawCount (parameter type int matches rawCount's return type)
        @Feature(ttl = "2h", description = "Normalised count")
        public double normCount(String entityId, int rawCount) {
            return rawCount / 100.0;
        }
    }

    static class CyclicFeatures {
        @Feature(ttl = "1h")
        public int a(String id, double b) { return (int) b; }

        @Feature(ttl = "1h")
        public double b(String id, int a) { return a; }
    }

    static class NoFeatures {
        public String notAFeature(String id) { return id; }
    }

    // -------------------------------------------------------------------------
    // TtlParser
    // -------------------------------------------------------------------------

    @Test
    void ttlParser_parsesHours() {
        assertThat(TtlParser.parse("1h")).hasHours(1);
    }

    @Test
    void ttlParser_parsesMinutes() {
        assertThat(TtlParser.parse("30m")).hasMinutes(30);
    }

    @Test
    void ttlParser_parsesDays() {
        assertThat(TtlParser.parse("7d")).hasDays(7);
    }

    @Test
    void ttlParser_parsesSeconds() {
        assertThat(TtlParser.parse("60s")).hasSeconds(60);
    }

    @Test
    void ttlParser_throwsForUnknownUnit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TtlParser.parse("5w"))
                .withMessageContaining("Unknown TTL unit");
    }

    @Test
    void ttlParser_throwsForBlank() {
        assertThatIllegalArgumentException().isThrownBy(() -> TtlParser.parse(""));
    }

    // -------------------------------------------------------------------------
    // FeatureClass.scan
    // -------------------------------------------------------------------------

    @Test
    void scan_detectsAllAnnotatedMethods() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        assertThat(fc.descriptors()).hasSize(2);
    }

    @Test
    void scan_ignoresUnannotatedMethods() {
        FeatureClass fc = FeatureClass.scan(NoFeatures.class);
        assertThat(fc.descriptors()).isEmpty();
    }

    @Test
    void scan_capturesName() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        List<String> names = fc.descriptors().stream().map(FeatureDescriptor::name).toList();
        assertThat(names).contains("txCount", "avgBasket");
    }

    @Test
    void scan_capturesTtl() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        FeatureDescriptor tx = fc.find("txCount").orElseThrow();
        assertThat(tx.ttl()).hasHours(1);
    }

    @Test
    void scan_capturesDescription() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        assertThat(fc.find("txCount").orElseThrow().description()).isEqualTo("Transaction count");
    }

    @Test
    void scan_capturesVersion() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        assertThat(fc.find("avgBasket").orElseThrow().version()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // Dependency resolution
    // -------------------------------------------------------------------------

    @Test
    void scan_detectsDependency_byReturnTypeMatch() {
        FeatureClass fc = FeatureClass.scan(DependentFeatures.class);
        FeatureDescriptor norm = fc.find("normCount").orElseThrow();
        assertThat(norm.dependencies()).containsExactly("rawCount");
    }

    @Test
    void scan_noDependencies_forSimpleFeature() {
        FeatureClass fc = FeatureClass.scan(SimpleFeatures.class);
        assertThat(fc.find("txCount").orElseThrow().dependencies()).isEmpty();
    }

    @Test
    void dependenciesOf_returnsDescriptors() {
        FeatureClass fc = FeatureClass.scan(DependentFeatures.class);
        FeatureDescriptor norm = fc.find("normCount").orElseThrow();
        List<FeatureDescriptor> deps = fc.dependenciesOf(norm);
        assertThat(deps).hasSize(1);
        assertThat(deps.get(0).name()).isEqualTo("rawCount");
    }

    // -------------------------------------------------------------------------
    // Topological order
    // -------------------------------------------------------------------------

    @Test
    void topologicalOrder_dependencyBeforeDependent() {
        FeatureClass fc = FeatureClass.scan(DependentFeatures.class);
        List<FeatureDescriptor> order = fc.topologicalOrder();
        int rawIdx  = indexByName(order, "rawCount");
        int normIdx = indexByName(order, "normCount");
        assertThat(rawIdx).isLessThan(normIdx);
    }

    @Test
    void topologicalOrder_cyclic_throwsIllegalState() {
        FeatureClass fc = FeatureClass.scan(CyclicFeatures.class);
        assertThatIllegalStateException()
                .isThrownBy(fc::topologicalOrder)
                .withMessageContaining("Cyclic");
    }

    // -------------------------------------------------------------------------

    private static int indexByName(List<FeatureDescriptor> list, String name) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).name().equals(name)) return i;
        }
        return -1;
    }
}
