package io.gauss.vigil.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MetricStreamService}. Covers HU-024 acceptance criteria.
 */
class MetricStreamServiceTest {

    private MetricStreamService svc;

    @BeforeEach
    void setUp() { svc = new MetricStreamService(); }

    @Test
    void log_incrementsCount() {
        svc.log("exp-1", "loss", 0.5, 0);
        assertThat(svc.count("exp-1", "loss")).isEqualTo(1);
    }

    @Test
    void all_returnsAllObservations() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.log("exp-1", "loss", 0.4, 1);
        svc.log("exp-1", "loss", 0.3, 2);
        assertThat(svc.all("exp-1", "loss")).hasSize(3);
    }

    @Test
    void all_emptyForUnknownExperiment() {
        assertThat(svc.all("unknown", "loss")).isEmpty();
    }

    @Test
    void all_emptyForUnknownMetric() {
        svc.log("exp-1", "loss", 0.5, 0);
        assertThat(svc.all("exp-1", "auc")).isEmpty();
    }

    @Test
    void since_returnsOnlyStepsAfterFromStep() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.log("exp-1", "loss", 0.4, 1);
        svc.log("exp-1", "loss", 0.3, 2);
        List<StepMetric> result = svc.since("exp-1", "loss", 0);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).step()).isEqualTo(1);
        assertThat(result.get(1).step()).isEqualTo(2);
    }

    @Test
    void since_returnsEmpty_whenNoNewSteps() {
        svc.log("exp-1", "loss", 0.5, 0);
        assertThat(svc.since("exp-1", "loss", 0)).isEmpty();
    }

    @Test
    void since_returnsAll_whenFromStepIsMinusOne() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.log("exp-1", "loss", 0.4, 1);
        assertThat(svc.since("exp-1", "loss", -1)).hasSize(2);
    }

    @Test
    void stepMetric_valuesCorrect() {
        svc.log("exp-1", "auc", 0.92, 5);
        StepMetric m = svc.all("exp-1", "auc").get(0);
        assertThat(m.metricName()).isEqualTo("auc");
        assertThat(m.value()).isEqualTo(0.92);
        assertThat(m.step()).isEqualTo(5);
        assertThat(m.experimentId()).isEqualTo("exp-1");
    }

    @Test
    void metricNames_returnsAllLogged() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.log("exp-1", "auc",  0.8, 0);
        assertThat(svc.metricNames("exp-1")).containsExactlyInAnyOrder("loss", "auc");
    }

    @Test
    void multipleExperiments_areIsolated() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.log("exp-2", "loss", 0.9, 0);
        assertThat(svc.count("exp-1", "loss")).isEqualTo(1);
        assertThat(svc.count("exp-2", "loss")).isEqualTo(1);
    }

    @Test
    void clear_removesExperimentData() {
        svc.log("exp-1", "loss", 0.5, 0);
        svc.clear("exp-1");
        assertThat(svc.count("exp-1", "loss")).isZero();
    }
}
