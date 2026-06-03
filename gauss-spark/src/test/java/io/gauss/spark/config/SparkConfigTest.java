package io.gauss.spark.config;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.SparkExecution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SparkConfig} and {@link SparkExecutionDescriptor}.
 * Covers HU-015 acceptance criteria — no actual Spark runtime needed.
 */
class SparkConfigTest {

    // -------------------------------------------------------------------------
    // Fixture pipeline classes
    // -------------------------------------------------------------------------

    @DataPipeline("churn-features")
    @SparkExecution(master = "spark://cluster:7077",
                    appName = "churn-etl",
                    executorMemory = "4g",
                    executorCores = 4,
                    adaptiveQueryExecution = true)
    static class ClusterPipeline { }

    @DataPipeline("local-etl")
    @SparkExecution  // all defaults
    static class DefaultSparkPipeline { }

    @DataPipeline("no-spark")
    static class NoSparkPipeline { }

    @DataPipeline("blank-app-name")
    @SparkExecution(appName = "")  // should fall back to pipeline name
    static class BlankAppNamePipeline { }

    // -------------------------------------------------------------------------
    // SparkConfig.from(Class)
    // -------------------------------------------------------------------------

    @Test
    void from_parsesmaster() {
        assertThat(SparkConfig.from(ClusterPipeline.class).master())
                .isEqualTo("spark://cluster:7077");
    }

    @Test
    void from_parsesAppName() {
        assertThat(SparkConfig.from(ClusterPipeline.class).appName())
                .isEqualTo("churn-etl");
    }

    @Test
    void from_parsesExecutorMemory() {
        assertThat(SparkConfig.from(ClusterPipeline.class).executorMemory())
                .isEqualTo("4g");
    }

    @Test
    void from_parsesExecutorCores() {
        assertThat(SparkConfig.from(ClusterPipeline.class).executorCores())
                .isEqualTo(4);
    }

    @Test
    void from_parsesAqe() {
        assertThat(SparkConfig.from(ClusterPipeline.class).adaptiveQueryExecution())
                .isTrue();
    }

    @Test
    void from_defaultMaster_isLocalAll() {
        assertThat(SparkConfig.from(DefaultSparkPipeline.class).master())
                .isEqualTo("local[*]");
    }

    @Test
    void from_blankAppName_fallsBackToPipelineName() {
        assertThat(SparkConfig.from(BlankAppNamePipeline.class).appName())
                .isEqualTo("blank-app-name");
    }

    @Test
    void from_noAnnotation_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SparkConfig.from(NoSparkPipeline.class));
    }

    // -------------------------------------------------------------------------
    // SparkConfig.local() factory
    // -------------------------------------------------------------------------

    @Test
    void local_masterStartsWithLocal() {
        assertThat(SparkConfig.local().master()).startsWith("local");
    }

    @Test
    void local_isLocal_true() {
        assertThat(SparkConfig.local().isLocal()).isTrue();
    }

    @Test
    void clusterConfig_isLocal_false() {
        assertThat(SparkConfig.from(ClusterPipeline.class).isLocal()).isFalse();
    }

    @Test
    void localWithThreads_containsThreadCount() {
        assertThat(SparkConfig.local(4).master()).isEqualTo("local[4]");
    }

    // -------------------------------------------------------------------------
    // SparkExecutionDescriptor.scan
    // -------------------------------------------------------------------------

    @Test
    void scan_pipelineName_fromAnnotation() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(ClusterPipeline.class);
        assertThat(d.pipelineName()).isEqualTo("churn-features");
    }

    @Test
    void scan_hasSparkExecution_true_whenAnnotated() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(ClusterPipeline.class);
        assertThat(d.hasSparkExecution()).isTrue();
    }

    @Test
    void scan_hasSparkExecution_false_whenNotAnnotated() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(NoSparkPipeline.class);
        assertThat(d.hasSparkExecution()).isFalse();
    }

    @Test
    void scan_config_presentWhenAnnotated() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(ClusterPipeline.class);
        assertThat(d.config()).isPresent();
    }

    @Test
    void scan_config_emptyWhenNotAnnotated() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(NoSparkPipeline.class);
        assertThat(d.config()).isEmpty();
    }

    @Test
    void scan_effectiveConfig_returnsLocalFallbackWhenNoAnnotation() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(NoSparkPipeline.class);
        assertThat(d.effectiveConfig().isLocal()).isTrue();
    }

    @Test
    void scan_noDataPipelineAnnotation_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SparkExecutionDescriptor.scan(Object.class));
    }

    @Test
    void scan_pipelineClass_stored() {
        SparkExecutionDescriptor d = SparkExecutionDescriptor.scan(ClusterPipeline.class);
        assertThat(d.pipelineClass()).isEqualTo(ClusterPipeline.class);
    }
}
