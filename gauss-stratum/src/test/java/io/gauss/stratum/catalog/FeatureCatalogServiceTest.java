package io.gauss.stratum.catalog;

import io.gauss.core.annotation.Feature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link FeatureCatalogService}.
 * Covers HU-030 acceptance criteria.
 */
class FeatureCatalogServiceTest {

    // -------------------------------------------------------------------------
    // Fixture feature classes
    // -------------------------------------------------------------------------

    static class ChurnFeatures {
        @Feature(ttl = "1h", description = "30-day transaction count")
        public int txCount30d(String id) { return 0; }

        @Feature(ttl = "6h", description = "Customer lifetime value")
        public double clv(String id) { return 0.0; }
    }

    static class NlpFeatures {
        @Feature(ttl = "24h", description = "Sentiment score of last review")
        public float sentimentScore(String id) { return 0f; }
    }

    private FeatureCatalogService catalog;

    @BeforeEach
    void setUp() {
        catalog = new FeatureCatalogService(ChurnFeatures.class, NlpFeatures.class);
    }

    // -------------------------------------------------------------------------
    // listAll
    // -------------------------------------------------------------------------

    @Test
    void listAll_returnsAllFeatures_acrossClasses() {
        assertThat(catalog.listAll()).hasSize(3);
    }

    @Test
    void listAll_containsCorrectNames() {
        List<String> names = catalog.listAll().stream().map(FeatureCatalogEntry::name).toList();
        assertThat(names).contains("txCount30d", "clv", "sentimentScore");
    }

    @Test
    void listAll_includesDescription() {
        FeatureCatalogEntry e = catalog.find("txCount30d").orElseThrow();
        assertThat(e.description()).isEqualTo("30-day transaction count");
    }

    @Test
    void listAll_includesReturnTypeName() {
        assertThat(catalog.find("txCount30d").orElseThrow().returnTypeName()).isEqualTo("int");
        assertThat(catalog.find("clv").orElseThrow().returnTypeName()).isEqualTo("double");
    }

    @Test
    void listAll_includesTtl() {
        assertThat(catalog.find("txCount30d").orElseThrow().ttl()).hasHours(1);
        assertThat(catalog.find("sentimentScore").orElseThrow().ttl()).hasDays(1);
    }

    // -------------------------------------------------------------------------
    // search
    // -------------------------------------------------------------------------

    @Test
    void search_byName_returnsMatchingEntries() {
        List<FeatureCatalogEntry> results = catalog.search("clv");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("clv");
    }

    @Test
    void search_caseInsensitive() {
        assertThat(catalog.search("SENTIMENT")).hasSize(1);
    }

    @Test
    void search_byDescription_returnsMatching() {
        List<FeatureCatalogEntry> results = catalog.search("transaction");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).name()).isEqualTo("txCount30d");
    }

    @Test
    void search_noMatch_returnsEmpty() {
        assertThat(catalog.search("xyz_nonexistent")).isEmpty();
    }

    @Test
    void search_blank_returnsAll() {
        assertThat(catalog.search("")).hasSize(3);
        assertThat(catalog.search("  ")).hasSize(3);
    }

    // -------------------------------------------------------------------------
    // find
    // -------------------------------------------------------------------------

    @Test
    void find_returnsEntry_whenPresent() {
        assertThat(catalog.find("clv")).isPresent();
    }

    @Test
    void find_returnsEmpty_whenAbsent() {
        assertThat(catalog.find("nonexistent")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // findByClass
    // -------------------------------------------------------------------------

    @Test
    void findByClass_returnsOnlyThatClassFeatures() {
        List<FeatureCatalogEntry> churnEntries =
                catalog.findByClass(ChurnFeatures.class.getName());
        assertThat(churnEntries).hasSize(2);
        assertThat(churnEntries).allMatch(
                e -> e.featureClass().equals(ChurnFeatures.class.getName()));
    }
}
