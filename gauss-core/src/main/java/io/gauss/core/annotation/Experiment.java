package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a training method for automatic experiment tracking (Vigil module).
 *
 * <p>When the annotated method is called, Gauss automatically:
 * <ul>
 *   <li>Creates an experiment run with a unique ID and timestamp.</li>
 *   <li>Records all method parameters as experiment parameters.</li>
 *   <li>Versions and stores any model artefact returned by the method.</li>
 *   <li>Makes the run available in the Vigil comparison dashboard.</li>
 * </ul>
 *
 * <p>Use {@code ExperimentContext} (injected by the framework) to log
 * metrics and artefacts from within the annotated method.
 *
 * <pre>{@code
 * @Experiment(name = "churn-xgboost", tags = {"xgboost", "churn"})
 * public TrainedModel train(double learningRate, int maxDepth,
 *                           ExperimentContext ctx) {
 *     TrainedModel model = XGBoost.train(data, learningRate, maxDepth);
 *     ctx.logMetric("auc",  model.getAuc());
 *     ctx.logMetric("f1",   model.getF1());
 *     ctx.logArtifact("confusion_matrix", model.confusionMatrix());
 *     return model;
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Experiment {

    /** Experiment group name. Multiple runs sharing a name are compared together. */
    String name() default "";

    /** Free-form tags for filtering in the dashboard. */
    String[] tags() default {};
}
