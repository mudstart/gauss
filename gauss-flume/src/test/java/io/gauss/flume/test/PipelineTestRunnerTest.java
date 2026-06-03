package io.gauss.flume.test;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import io.gauss.flume.runner.PipelineExecutionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PipelineTestRunner}.
 */
class PipelineTestRunnerTest {

    // -------------------------------------------------------------------------
    // Fixture
    // -------------------------------------------------------------------------

    @DataPipeline("greeting-pipeline")
    static class GreetingPipeline {
        @Ingest(source = "custom://greetings/list")
        public List<String> loadNames() {
            return null;  // filled by SourceReader / mock
        }

        @Transform("greet")
        public String greet(List<String> names) {
            return "Hello, " + String.join(" & ", names) + "!";
        }
    }

    @DataPipeline("fallback-pipeline")
    static class FallbackPipeline {
        @Ingest(source = "memory://noop")
        public String load() { return "from-method"; }
    }

    @DataPipeline("multi-ingest")
    static class MultiIngestPipeline {
        @Ingest(source = "custom://source/a")
        public String loadA() { return null; }

        @Transform
        public String combine(String a) { return a + "-combined"; }
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void mockSource_injectedInsteadOfMethodBody() {
        Object result = new PipelineTestRunner()
                .mockSource("custom://greetings/list", "[\"Alice\",\"Bob\"]")
                .run(GreetingPipeline.class);

        assertThat(result).isEqualTo("Hello, Alice & Bob!");
    }

    @Test
    void run_byName_worksWithMock() {
        Object result = new PipelineTestRunner()
                .mockSource("custom://greetings/list", "[\"Charlie\"]")
                .register(GreetingPipeline.class)
                .run("greeting-pipeline");

        assertThat(result).isEqualTo("Hello, Charlie!");
    }

    @Test
    void multipleRunners_areIsolated() {
        // Two runners with different mock data should produce independent results
        PipelineTestRunner runner1 = new PipelineTestRunner()
                .mockSource("custom://source/a", "\"first\"");
        PipelineTestRunner runner2 = new PipelineTestRunner()
                .mockSource("custom://source/a", "\"second\"");

        assertThat(runner1.run(MultiIngestPipeline.class)).isEqualTo("first-combined");
        assertThat(runner2.run(MultiIngestPipeline.class)).isEqualTo("second-combined");
    }

    @Test
    void run_unannotatedClass_throwsIllegalArgument() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PipelineTestRunner().run(String.class))
                .withMessageContaining("@DataPipeline");
    }

    @Test
    void run_withoutMock_methodBodyIsUsedAsFallback() {
        // "memory://noop" has no matching reader → PipelineRunner falls back to method body
        Object result = new PipelineTestRunner().run(FallbackPipeline.class);
        assertThat(result).isEqualTo("from-method");
    }
}
