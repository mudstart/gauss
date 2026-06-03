package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Applies circuit-breaker protection to a {@link MLEndpoint} prediction method
 * (Augur module, HU-053).
 *
 * <p>When the number of consecutive failures reaches {@link #threshold}, the
 * circuit opens and all subsequent calls immediately invoke the
 * {@link #fallback} method instead of the real prediction logic.  After
 * {@link #delay} the circuit moves to HALF-OPEN and allows one probe call to
 * test recovery.
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnEndpoint {
 *
 *     @CircuitBreaker(threshold = 5, delay = "30s", fallback = "safePrediction")
 *     public double predict(CustomerInput input) { ... }
 *
 *     public double safePrediction(CustomerInput input) { return 0.5; }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CircuitBreaker {

    /**
     * Number of consecutive failures before the circuit opens.
     * Must be &gt; 0.  Defaults to {@code 5}.
     */
    int threshold() default 5;

    /**
     * How long to keep the circuit open before moving to HALF-OPEN.
     * Expressed as a duration string ({@code "30s"}, {@code "1m"}, etc.).
     * Defaults to {@code "30s"}.
     */
    String delay() default "30s";

    /**
     * Name of the fallback method in the same class.  The fallback must have
     * the same parameter list and return type as the annotated method.
     */
    String fallback() default "";
}
