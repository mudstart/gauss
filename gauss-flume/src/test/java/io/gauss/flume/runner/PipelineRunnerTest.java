package io.gauss.flume.runner;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PipelineRunner}.
 * Covers HU-011 acceptance criterion 5: pipelines can be executed by name.
 */
class PipelineRunnerTest {

    // -------------------------------------------------------------------------
    // Fixture types and pipeline
    // -------------------------------------------------------------------------

    static class RawInput  { final String value; RawInput(String v)  { this.value = v; } }
    static class Processed { final String value; Processed(String v) { this.value = v; } }
    static class Output    { final String value; Output(String v)    { this.value = v; } }

    @DataPipeline("test-pipeline")
    static class TestPipeline {
        // "memory://" scheme has no built-in reader → falls back to method body
        @Ingest(source = "memory://test/data")
        public RawInput load() { return new RawInput("raw"); }

        @Transform("process")
        public Processed process(RawInput input) {
            return new Processed(input.value + "-processed");
        }

        @Transform("finalise")
        public Output finalise(Processed p) {
            return new Output(p.value + "-done");
        }
    }

    @DataPipeline("ingest-only")
    static class IngestOnlyPipeline {
        // "memory://" has no built-in reader → falls back to method body
        @Ingest(source = "memory://db/table")
        public RawInput load() { return new RawInput("from-db"); }
    }

    @DataPipeline("failing-pipeline")
    static class FailingPipeline {
        // "memory://" has no built-in reader → method body is invoked and throws
        @Ingest(source = "memory://bad/source")
        public RawInput load() { throw new RuntimeException("simulated I/O error"); }
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    private PipelineRunner runner;

    @BeforeEach
    void setUp() {
        runner = new PipelineRunner();
    }

    // -------------------------------------------------------------------------
    // AC-5: PipelineRunner.run("name") executes the pipeline
    // -------------------------------------------------------------------------

    @Test
    void run_executesPipelineByName() {
        runner.register(TestPipeline.class);
        Object result = runner.run("test-pipeline");

        assertThat(result).isInstanceOf(Output.class);
        assertThat(((Output) result).value).isEqualTo("raw-processed-done");
    }

    @Test
    void run_ingestOnlyPipeline_returnsIngestResult() {
        runner.register(IngestOnlyPipeline.class);
        Object result = runner.run("ingest-only");

        assertThat(result).isInstanceOf(RawInput.class);
        assertThat(((RawInput) result).value).isEqualTo("from-db");
    }

    @Test
    void run_throwsForUnregisteredPipeline() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> runner.run("ghost-pipeline"))
                .withMessageContaining("ghost-pipeline");
    }

    @Test
    void run_wrapsStepExceptionInPipelineExecutionException() {
        runner.register(FailingPipeline.class);

        assertThatExceptionOfType(PipelineExecutionException.class)
                .isThrownBy(() -> runner.run("failing-pipeline"))
                .withMessageContaining("load")
                .withCauseInstanceOf(RuntimeException.class)
                .havingCause().withMessage("simulated I/O error");
    }

    @Test
    void isRegistered_returnsTrueAfterRegister() {
        assertThat(runner.isRegistered("test-pipeline")).isFalse();
        runner.register(TestPipeline.class);
        assertThat(runner.isRegistered("test-pipeline")).isTrue();
    }
}
