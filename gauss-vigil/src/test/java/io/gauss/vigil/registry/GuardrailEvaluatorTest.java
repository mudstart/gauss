package io.gauss.vigil.registry;

import io.gauss.vigil.experiment.ExperimentRunner;
import io.gauss.vigil.experiment.InMemoryExperimentStore;
import io.gauss.vigil.experiment.ExperimentRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GuardrailEvaluator}.
 * Covers HU-043 acceptance criteria.
 */
class GuardrailEvaluatorTest {

    private GuardrailEvaluator evaluator;
    private InMemoryExperimentStore store;
    private ExperimentRunner runner;

    @BeforeEach
    void setUp() {
        evaluator = new GuardrailEvaluator();
        store     = new InMemoryExperimentStore();
        runner    = new ExperimentRunner(store);
    }

    // Helper: create a run with given metrics
    private ExperimentRun runWith(Map<String, Double> metrics) {
        runner.run("test-exp", ctx -> {
            metrics.forEach(ctx::logMetric);
            return null;
        });
        return store.findAll().get(store.findAll().size() - 1);
    }

    // Helper: read guardrails from an inner annotated class
    @ModelGuardrail(metric = "auc", min = 0.90)
    private static class AucGuardrail {}

    @ModelGuardrail(metric = "auc", min = 0.90)
    @ModelGuardrail(metric = "f1",  min = 0.85)
    private static class MultiGuardrail {}

    @ModelGuardrail(metric = "rmse", max = 0.15)
    private static class MaxGuardrail {}

    // -------------------------------------------------------------------------
    // Map-based evaluation
    // -------------------------------------------------------------------------

    @Test
    void evaluate_map_passes_whenMetricMeetsMin() {
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatCode(() -> evaluator.evaluate(Map.of("auc", 0.95), g))
                .doesNotThrowAnyException();
    }

    @Test
    void evaluate_map_throws_whenMetricBelowMin() {
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatExceptionOfType(GuardrailViolationException.class)
                .isThrownBy(() -> evaluator.evaluate(Map.of("auc", 0.85), g))
                .satisfies(ex -> {
                    assertThat(ex.metric()).isEqualTo("auc");
                    assertThat(ex.actualValue()).isEqualTo(0.85);
                    assertThat(ex.getMessage()).contains("0.85").contains("0.9");
                });
    }

    @Test
    void evaluate_map_passes_whenMetricMeetsMax() {
        ModelGuardrail[] g = MaxGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatCode(() -> evaluator.evaluate(Map.of("rmse", 0.10), g))
                .doesNotThrowAnyException();
    }

    @Test
    void evaluate_map_throws_whenMetricExceedsMax() {
        ModelGuardrail[] g = MaxGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatExceptionOfType(GuardrailViolationException.class)
                .isThrownBy(() -> evaluator.evaluate(Map.of("rmse", 0.20), g));
    }

    @Test
    void evaluate_map_multipleGuardrails_allPassing() {
        ModelGuardrail[] g = MultiGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatCode(() -> evaluator.evaluate(Map.of("auc", 0.92, "f1", 0.88), g))
                .doesNotThrowAnyException();
    }

    @Test
    void evaluate_map_multipleGuardrails_secondFailing() {
        ModelGuardrail[] g = MultiGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatExceptionOfType(GuardrailViolationException.class)
                .isThrownBy(() -> evaluator.evaluate(Map.of("auc", 0.92, "f1", 0.80), g))
                .satisfies(ex -> assertThat(ex.metric()).isEqualTo("f1"));
    }

    @Test
    void evaluate_map_missingMetric_throwsIllegalArgument() {
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluator.evaluate(Map.of("f1", 0.88), g))
                .withMessageContaining("auc");
    }

    // -------------------------------------------------------------------------
    // ExperimentRun-based evaluation
    // -------------------------------------------------------------------------

    @Test
    void evaluate_run_passes_whenMetricMeetsThreshold() {
        ExperimentRun run = runWith(Map.of("auc", 0.95));
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatCode(() -> evaluator.evaluate(run, g)).doesNotThrowAnyException();
    }

    @Test
    void evaluate_run_throws_whenMetricBelowThreshold() {
        ExperimentRun run = runWith(Map.of("auc", 0.80));
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatExceptionOfType(GuardrailViolationException.class)
                .isThrownBy(() -> evaluator.evaluate(run, g));
    }

    @Test
    void evaluate_run_missingMetric_throwsIllegalArgument() {
        ExperimentRun run = runWith(Map.of("f1", 0.88));
        ModelGuardrail[] g = AucGuardrail.class.getAnnotationsByType(ModelGuardrail.class);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> evaluator.evaluate(run, g))
                .withMessageContaining("auc");
    }
}
