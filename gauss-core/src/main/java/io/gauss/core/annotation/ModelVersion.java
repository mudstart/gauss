package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares one version of a model endpoint and its traffic weight for A/B
 * serving (Augur module, HU-019).
 *
 * <p>Multiple {@code @ModelVersion} annotations on the same {@code @MLEndpoint}
 * class configure weighted traffic splitting.  Weights are relative integers;
 * the framework normalises them to percentages at startup.
 *
 * <pre>{@code
 * @MLEndpoint
 * @ModelVersion(value = "v1", weight = 80)
 * @ModelVersion(value = "v2", weight = 20)
 * public class ChurnEndpoint { ... }
 * }</pre>
 *
 * <p>Traffic weights can be updated at runtime via the admin API without
 * redeploying the application.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ModelVersions.class)
public @interface ModelVersion {

    /** Version identifier string (e.g., {@code "v1"}, {@code "2024-12-01"}). */
    String value();

    /**
     * Relative traffic weight for this version (positive integer).
     * A weight of {@code 80} alongside another version with weight {@code 20}
     * means this version receives 80 % of requests.
     */
    int weight() default 100;

    /**
     * Minimum sample size before A/B statistical evaluation begins
     * (used by {@code StatisticalTestService}).  {@code 0} means evaluate
     * immediately.
     */
    int minSampleSize() default 0;
}
