package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Activates automatic data drift monitoring for a {@link MLEndpoint} class
 * (Augur module, HU-037).
 *
 * <p>The framework accumulates prediction inputs and compares their distribution
 * against the reference distribution recorded when the model was registered.
 * An alert is raised when the chosen drift metric exceeds {@link #threshold}.
 *
 * <pre>{@code
 * @MLEndpoint
 * @DriftMonitor(threshold = 0.1, metric = "PSI", sampleSize = 500)
 * public class ChurnEndpoint { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DriftMonitor {

    /**
     * Drift metric to compute.  Supported values:
     * {@code "PSI"} (Population Stability Index, default),
     * {@code "KL"} (KL divergence).
     */
    String metric() default "PSI";

    /**
     * Score above which a drift alert is raised.  PSI guidelines:
     * {@code < 0.1} = stable, {@code 0.1–0.25} = moderate change,
     * {@code > 0.25} = major shift.  Defaults to {@code 0.1}.
     */
    double threshold() default 0.1;

    /**
     * Number of prediction inputs to accumulate before computing drift.
     * Defaults to {@code 100}.
     */
    int sampleSize() default 100;
}
