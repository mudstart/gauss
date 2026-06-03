package io.gauss.spark.runner;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.SparkExecution;
import io.gauss.core.annotation.Transform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LocalSparkPipelineRunner} and {@link SparkPipelineRunner}.
 * Covers HU-015 acceptance criteria — runs entirely without a Spark cluster.
 */
class LocalSparkPipelineRunnerTest {

    // -------------------------------------------------------------------------
    // Fixture pipelines
    // -------------------------------------------------------------------------

    @DataPipeline("simple-etl")
    @SparkExecution(master = "local[2]", appName = "test-job")
    static class SimpleEtlPipeline {

        @Ingest(source = "memory://test-data")
        public List<String> load() {
            return List.of("alpha", "beta", "gamma");
        }

        @Transform
        public List<String> uppercase(List<String> data) {
            return data.stream().map(String::toUpperCase).toList();
        }
    }

    @DataPipeline("no-transform")
    @SparkExecution
    static class IngestOnlyPipeline {

        @Ingest(source = "memory://data")
        public List<Integer> numbers() {
            return List.of(1, 2, 3, 4, 5);
        }
    }

    @DataPipeline("empty-pipeline")
    @SparkExecution
    static class EmptyPipeline { }

    @DataPipeline("multi-ingest")
    @SparkExecution
    static class MultiIngestPipeline {

        @Ingest(source = "memory://a")
        public List<String> sourceA() { return List.of("a1", "a2"); }

        @Ingest(source = "memory://b")
        public List<String> sourceB() { return List.of("b1"); }

        @Transform
        public List<String> merge(List<String> dataA) {
            return dataA;
        }
    }

    // -------------------------------------------------------------------------

    private LocalSparkPipelineRunner runner;

    @BeforeEach
    void setUp() {
        runner = new LocalSparkPipelineRunner();
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    @Test
    void run_completesWithoutException() {
        assertThatNoException()
                .isThrownBy(() -> runner.run(new SimpleEtlPipeline()));
    }

    @Test
    void run_pipelineName_correct() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        assertThat(result.pipelineName()).isEqualTo("simple-etl");
    }

    @Test
    void run_executedLocally_isTrue() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        assertThat(result.executedLocally()).isTrue();
    }

    @Test
    void run_recordsRead_matchesIngestOutput() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        // load() returns 3 items
        assertThat(result.recordsRead()).isEqualTo(3);
    }

    @Test
    void run_recordsWritten_matchesLastTransformOutput() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        // uppercase() returns 3 items
        assertThat(result.recordsWritten()).isEqualTo(3);
    }

    @Test
    void run_duration_isNonNegative() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        assertThat(result.duration().toMillis()).isGreaterThanOrEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Ingest-only pipeline
    // -------------------------------------------------------------------------

    @Test
    void run_ingestOnly_recordsRead_correct() {
        SparkJobResult result = runner.run(new IngestOnlyPipeline());
        assertThat(result.recordsRead()).isEqualTo(5);
    }

    // -------------------------------------------------------------------------
    // Empty pipeline
    // -------------------------------------------------------------------------

    @Test
    void run_emptyPipeline_doesNotFail() {
        assertThatNoException()
                .isThrownBy(() -> runner.run(new EmptyPipeline()));
    }

    @Test
    void run_emptyPipeline_zeroRecords() {
        SparkJobResult result = runner.run(new EmptyPipeline());
        assertThat(result.recordsRead()).isZero();
    }

    // -------------------------------------------------------------------------
    // SparkJobResult
    // -------------------------------------------------------------------------

    @Test
    void result_summary_containsPipelineName() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        assertThat(result.summary()).contains("simple-etl");
    }

    @Test
    void result_summary_containsLocalMode() {
        SparkJobResult result = runner.run(new SimpleEtlPipeline());
        assertThat(result.summary()).contains("LOCAL");
    }

    // -------------------------------------------------------------------------
    // SparkPipelineRunner (auto-detects Spark absence → falls back to local)
    // -------------------------------------------------------------------------

    @Test
    void sparkPipelineRunner_isSparkAvailable_falseInTestEnvironment() {
        // In tests there is no Spark on the classpath (it's provided/optional)
        assertThat(SparkPipelineRunner.isSparkAvailable()).isFalse();
    }

    @Test
    void sparkPipelineRunner_fallsBackToLocal_whenSparkAbsent() {
        SparkPipelineRunner sparkRunner = new SparkPipelineRunner();
        SparkJobResult result = sparkRunner.run(new SimpleEtlPipeline());
        // Falls back to local since Spark is not on test classpath
        assertThat(result.executedLocally()).isTrue();
    }

    @Test
    void sparkPipelineRunner_run_completesSuccessfully() {
        SparkJobResult result = new SparkPipelineRunner().run(new SimpleEtlPipeline());
        assertThat(result.pipelineName()).isEqualTo("simple-etl");
    }
}
