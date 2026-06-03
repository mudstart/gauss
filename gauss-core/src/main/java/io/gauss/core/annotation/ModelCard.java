package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches structured documentation metadata to a model class (Vigil module, HU-055).
 *
 * <p>When present, the Vigil {@code ModelCardService} uses this annotation to
 * enrich the automatically generated Model Card with human-authored fields
 * describing intended use, known limitations, and training data provenance.
 *
 * <pre>{@code
 * @ModelCard(
 *     description   = "XGBoost model for 30-day churn prediction",
 *     intendedUse   = "Predict churn probability for B2C customers",
 *     limitations   = "Not suitable for B2B or enterprise segments",
 *     trainedOn     = "Customer transactions 2020-2024, ~5M rows"
 * )
 * @DataPipeline("churn-training")
 * public class ChurnTrainingPipeline { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModelCard {

    /** One-sentence description of what the model does. */
    String description() default "";

    /** Populations, scenarios, and tasks the model is designed for. */
    String intendedUse() default "";

    /** Known failure modes, data biases, and out-of-scope use cases. */
    String limitations() default "";

    /** Description of training data (source, time range, approximate size). */
    String trainedOn() default "";

    /**
     * Semantic version of this card (independent of the model version stored in
     * the registry).  Defaults to {@code "1.0"}.
     */
    String version() default "1.0";
}
