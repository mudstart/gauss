package io.gauss.vigil.registry;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a minimum (and optionally maximum) quality threshold that a model
 * must satisfy before it can be promoted to {@link Stage#PRODUCTION} (HU-043).
 *
 * <p>Multiple guardrails may be stacked on the same target:
 * <pre>{@code
 * @ModelGuardrail(metric = "auc",  min = 0.90)
 * @ModelGuardrail(metric = "f1",   min = 0.85)
 * @ModelGuardrail(metric = "rmse", max = 0.15)
 * public TrainedModel train(double lr, ExperimentContext ctx) { ... }
 * }</pre>
 *
 * <p>Guardrails are evaluated by {@link GuardrailEvaluator} when
 * {@link ModelRegistry#promote(String, Stage, String)} is called with
 * {@link Stage#PRODUCTION}.  A {@link GuardrailViolationException} is thrown
 * if any threshold is not met, blocking the promotion.
 *
 * @see GuardrailEvaluator
 * @see ModelRegistry
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Repeatable(ModelGuardrails.class)
public @interface ModelGuardrail {

    /** Name of the metric to evaluate (as logged with {@code ctx.logMetric(...)}). */
    String metric();

    /** Minimum acceptable value (inclusive). Defaults to no lower bound. */
    double min() default Double.NEGATIVE_INFINITY;

    /** Maximum acceptable value (inclusive). Defaults to no upper bound. */
    double max() default Double.POSITIVE_INFINITY;
}
