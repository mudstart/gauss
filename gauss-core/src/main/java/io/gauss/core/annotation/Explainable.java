package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Attaches Kernel SHAP explainability to an {@link MLEndpoint} prediction
 * method (Augur module, HU-059).
 *
 * <p>When present, the framework computes SHAP values for each prediction and
 * appends the top-{@link #topFeatures} most influential features to the response.
 * The computation is asynchronous by default so it does not increase the
 * synchronous prediction latency.
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnEndpoint {
 *
 *     @Explainable(topFeatures = 5)
 *     public double predict(CustomerInput input) { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Explainable {

    /**
     * Maximum number of SHAP values to include in the response,
     * ordered by {@code |impact|} descending.  Defaults to {@code 5}.
     */
    int topFeatures() default 5;

    /**
     * If {@code true} (default), SHAP is computed in a background thread and
     * the prediction is returned immediately.  Set to {@code false} to compute
     * synchronously (increases latency but guarantees SHAP is present in the
     * synchronous response).
     */
    boolean async() default true;
}
