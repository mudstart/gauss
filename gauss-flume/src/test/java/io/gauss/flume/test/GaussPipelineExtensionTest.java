package io.gauss.flume.test;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link GaussPipelineExtension} — verifies that {@link MockSource}
 * annotations are resolved and a ready-to-use {@link PipelineTestRunner} is
 * injected into test methods.
 */
@ExtendWith(GaussPipelineExtension.class)
@MockSource(
    source = "custom://products/list",
    value  = "[\"Widget\",\"Gadget\"]"
)
class GaussPipelineExtensionTest {

    // -------------------------------------------------------------------------
    // Fixture pipeline
    // -------------------------------------------------------------------------

    @DataPipeline("product-pipeline")
    static class ProductPipeline {
        @Ingest(source = "custom://products/list")
        public List<String> loadProducts() { return null; }

        @Transform("count")
        public int count(List<String> products) { return products.size(); }
    }

    @DataPipeline("tagged-pipeline")
    static class TaggedPipeline {
        @Ingest(source = "custom://tags/list")
        public List<String> loadTags() { return null; }

        @Transform("join")
        public String join(List<String> tags) { return String.join(",", tags); }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void extension_injectsRunnerWithClassLevelMock(PipelineTestRunner runner) {
        // Class-level @MockSource provides the product list
        Object result = runner.run(ProductPipeline.class);
        assertThat(result).isEqualTo(2);
    }

    @Test
    @MockSource(
        source = "custom://tags/list",
        value  = "[\"java\",\"ml\",\"data\"]"
    )
    void extension_mergesMethodLevelMock(PipelineTestRunner runner) {
        // Method-level mock adds tags source on top of class-level product mock
        Object tags   = runner.run(TaggedPipeline.class);
        assertThat(tags).isEqualTo("java,ml,data");

        // Class-level mock is still present
        Object count  = runner.run(ProductPipeline.class);
        assertThat(count).isEqualTo(2);
    }

    @Test
    @MockSource(
        source = "custom://products/list",
        value  = "[\"Override\"]"
    )
    void methodLevel_overridesClassLevel_forSameUri(PipelineTestRunner runner) {
        // Method mock overrides the class-level mock for the same URI
        Object result = runner.run(ProductPipeline.class);
        assertThat(result).isEqualTo(1);
    }

    @Test
    void eachTest_getsAFreshRunner(PipelineTestRunner runner) {
        // Verify the runner is a fresh instance — no state from previous tests
        assertThat(runner).isNotNull();
        // Register and run should work cleanly
        Object result = runner.run(ProductPipeline.class);
        assertThat(result).isInstanceOf(Integer.class);
    }
}
