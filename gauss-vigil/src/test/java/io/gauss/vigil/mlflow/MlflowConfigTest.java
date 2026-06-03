package io.gauss.vigil.mlflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MlflowConfig}. Covers HU-026 acceptance criteria.
 */
class MlflowConfigTest {

    @Test
    void of_setsDefaultExperimentName() {
        MlflowConfig cfg = MlflowConfig.of("http://mlflow:5000");
        assertThat(cfg.experimentName()).isEqualTo("Default");
    }

    @Test
    void createRunUrl_appendsCorrectPath() {
        MlflowConfig cfg = MlflowConfig.of("http://mlflow:5000");
        assertThat(cfg.createRunUrl())
                .isEqualTo("http://mlflow:5000/api/2.0/mlflow/runs/create");
    }

    @Test
    void logMetricUrl_appendsCorrectPath() {
        MlflowConfig cfg = MlflowConfig.of("http://mlflow:5000");
        assertThat(cfg.logMetricUrl())
                .isEqualTo("http://mlflow:5000/api/2.0/mlflow/runs/log-metric");
    }

    @Test
    void listExperimentsUrl_appendsCorrectPath() {
        MlflowConfig cfg = MlflowConfig.of("http://mlflow:5000");
        assertThat(cfg.listExperimentsUrl())
                .isEqualTo("http://mlflow:5000/api/2.0/mlflow/experiments/list");
    }

    @Test
    void createRunUrl_stripsTrailingSlash() {
        MlflowConfig cfg = MlflowConfig.of("http://mlflow:5000/");
        assertThat(cfg.createRunUrl())
                .isEqualTo("http://mlflow:5000/api/2.0/mlflow/runs/create");
    }

    @Test
    void trackingUri_stored() {
        assertThat(new MlflowConfig("http://mlflow:5000", "MyExp").trackingUri())
                .isEqualTo("http://mlflow:5000");
    }

    @Test
    void experimentName_stored() {
        assertThat(new MlflowConfig("http://mlflow:5000", "MyExp").experimentName())
                .isEqualTo("MyExp");
    }
}
