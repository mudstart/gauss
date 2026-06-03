package io.gauss.vigil.registry;

/**
 * Thrown by {@link GuardrailEvaluator} when a model fails a
 * {@link ModelGuardrail} threshold check (HU-043).
 *
 * <p>Catching this exception from {@link ModelRegistry#promote} indicates that
 * the model does not meet the required quality bar for the target stage.
 */
public class GuardrailViolationException extends RuntimeException {

    private final String metric;
    private final double actualValue;
    private final ModelGuardrail guardrail;

    public GuardrailViolationException(String metric,
                                        double actualValue,
                                        ModelGuardrail guardrail) {
        super(buildMessage(metric, actualValue, guardrail));
        this.metric      = metric;
        this.actualValue = actualValue;
        this.guardrail   = guardrail;
    }

    /** Name of the metric that violated the guardrail. */
    public String metric() { return metric; }

    /** Actual value that was evaluated against the threshold. */
    public double actualValue() { return actualValue; }

    /** The guardrail that was violated. */
    public ModelGuardrail guardrail() { return guardrail; }

    // -------------------------------------------------------------------------

    private static String buildMessage(String metric, double actual, ModelGuardrail g) {
        StringBuilder sb = new StringBuilder(
                "Guardrail violation for metric '").append(metric).append("': ");
        sb.append("actual=").append(actual);
        if (g.min() != Double.NEGATIVE_INFINITY) sb.append(", min=").append(g.min());
        if (g.max() != Double.POSITIVE_INFINITY) sb.append(", max=").append(g.max());
        return sb.toString();
    }
}
