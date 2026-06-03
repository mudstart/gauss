package io.gauss.vigil.registry;

import io.gauss.vigil.experiment.ExperimentRun;

import java.util.Map;
import java.util.Optional;

/**
 * Evaluates {@link ModelGuardrail} thresholds against the metrics recorded
 * in an {@link ExperimentRun} (HU-043).
 *
 * <p>Called automatically by {@link ModelRegistry#promote} when the target
 * stage is {@link Stage#PRODUCTION}.
 */
public final class GuardrailEvaluator {

    /**
     * Evaluates all guardrails in the given array against the run's metrics.
     *
     * @param run        the experiment run whose metrics are evaluated
     * @param guardrails the guardrails to apply
     * @throws GuardrailViolationException if any guardrail is not satisfied
     * @throws IllegalArgumentException    if a guardrail refers to a metric
     *                                     that was never logged in the run
     */
    public void evaluate(ExperimentRun run, ModelGuardrail[] guardrails) {
        for (ModelGuardrail g : guardrails) {
            Optional<Double> optValue = run.latestMetric(g.metric());
            if (optValue.isEmpty()) {
                throw new IllegalArgumentException(
                        "Guardrail metric '" + g.metric()
                                + "' was not logged in experiment run '" + run.id() + "'");
            }
            double value = optValue.get();
            if (value < g.min() || value > g.max()) {
                throw new GuardrailViolationException(g.metric(), value, g);
            }
        }
    }

    /**
     * Evaluates a flat metric map (useful when no experiment run is available,
     * e.g. manual evaluation with a pre-computed metric set).
     *
     * @param metrics    metric name to value
     * @param guardrails the guardrails to apply
     * @throws GuardrailViolationException if any guardrail is not satisfied
     */
    public void evaluate(Map<String, Double> metrics, ModelGuardrail[] guardrails) {
        for (ModelGuardrail g : guardrails) {
            Double value = metrics.get(g.metric());
            if (value == null) {
                throw new IllegalArgumentException(
                        "Guardrail metric '" + g.metric() + "' not found in provided metric map");
            }
            if (value < g.min() || value > g.max()) {
                throw new GuardrailViolationException(g.metric(), value, g);
            }
        }
    }
}
