package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables automatic rollback of a model in production when the named metric
 * degrades beyond {@link #threshold} over a sliding window (Vigil module, HU-054).
 *
 * <p>When the rolling average of {@link #metric} for the model exceeds
 * {@link #threshold} during {@link #windowMinutes} minutes, the Vigil
 * {@code RollbackService} reverts the Model Registry to the previous production
 * version and emits a structured rollback event.
 *
 * <pre>{@code
 * @AutoRollback(
 *     metric         = "error_rate",
 *     threshold      = 0.15,
 *     windowMinutes  = 10,
 *     maxPerHour     = 3
 * )
 * @MLEndpoint
 * public class ChurnEndpoint { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface AutoRollback {

    /** Name of the metric to monitor (e.g., {@code "error_rate"}, {@code "latency_p99"}). */
    String metric();

    /**
     * Threshold value above which the rollback is triggered.
     * For error rates this is a fraction (e.g., {@code 0.15} = 15 %).
     */
    double threshold();

    /**
     * Sliding window duration in minutes over which the metric average is
     * computed.  Defaults to {@code 10}.
     */
    int windowMinutes() default 10;

    /**
     * Maximum number of automatic rollbacks allowed per hour for this endpoint,
     * to prevent oscillation.  Defaults to {@code 3}.
     */
    int maxPerHour() default 3;
}
