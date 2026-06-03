package io.gauss.vigil.experiment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ExperimentContext}.
 */
class ExperimentContextTest {

    private final ExperimentContext ctx = new ExperimentContext();

    @Test
    void logMetric_withoutStep_isRecorded() {
        ctx.logMetric("auc", 0.95);
        assertThat(ctx.metrics()).hasSize(1);
        assertThat(ctx.metrics().get(0).name()).isEqualTo("auc");
        assertThat(ctx.metrics().get(0).value()).isEqualTo(0.95);
        assertThat(ctx.metrics().get(0).step()).isEqualTo(-1);
    }

    @Test
    void logMetric_withStep_isRecorded() {
        ctx.logMetric("loss", 0.3, 10);
        assertThat(ctx.metrics()).hasSize(1);
        assertThat(ctx.metrics().get(0).step()).isEqualTo(10);
    }

    @Test
    void logMetric_multipleMetrics_allRecorded() {
        ctx.logMetric("auc", 0.95);
        ctx.logMetric("f1",  0.88);
        ctx.logMetric("auc", 0.97);  // second observation of same metric
        assertThat(ctx.metrics()).hasSize(3);
    }

    @Test
    void logArtifact_isRecorded() {
        ctx.logArtifact("confusion_matrix", new int[][]{{10, 2}, {1, 20}});
        assertThat(ctx.artifacts()).hasSize(1);
        assertThat(ctx.artifacts().get(0).name()).isEqualTo("confusion_matrix");
    }

    @Test
    void metrics_returnsUnmodifiableList() {
        ctx.logMetric("auc", 0.9);
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ctx.metrics().add(ExperimentMetric.of("x", 1.0, null)));
    }

    @Test
    void artifacts_returnsUnmodifiableList() {
        ctx.logArtifact("m", "data");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> ctx.artifacts().add(ExperimentArtifact.of("y", "z", null)));
    }

    @Test
    void timestampIsPopulated() {
        ctx.logMetric("auc", 0.9);
        assertThat(ctx.metrics().get(0).timestamp()).isNotNull();
    }
}
