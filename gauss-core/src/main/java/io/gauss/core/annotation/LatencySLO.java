package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares latency Service-Level Objectives (SLOs) for a {@link MLEndpoint}
 * prediction method or class (Augur module, HU-042).
 *
 * <p>The Gauss benchmark goal ({@code dsml:benchmark}) reads these annotations
 * and fails the build if the measured percentiles exceed the declared targets.
 *
 * <pre>{@code
 * @MLEndpoint
 * @LatencySLO(p99 = "50ms", p95 = "20ms", p50 = "5ms")
 * public class ChurnEndpoint {
 *     public double predict(CustomerInput input) { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface LatencySLO {

    /**
     * Maximum allowed p50 (median) latency, expressed as a duration string
     * (e.g., {@code "5ms"}, {@code "1s"}).  Empty string disables this SLO.
     */
    String p50() default "";

    /**
     * Maximum allowed p95 latency.  Empty string disables this SLO.
     */
    String p95() default "";

    /**
     * Maximum allowed p99 latency.  Empty string disables this SLO.
     */
    String p99() default "";
}
