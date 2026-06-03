package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables result caching for a prediction method (Augur module, HU-021).
 *
 * <p>When added to a method on a {@link MLEndpoint}-annotated class, the
 * framework caches the return value keyed by {@code (endpointName, inputHash)}.
 * Subsequent calls with identical inputs return the cached result without
 * invoking the model.
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnEndpoint {
 *
 *     @CachedPrediction(ttl = "5m")
 *     public double predict(CustomerInput input) {
 *         return model.score(input);
 *     }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CachedPrediction {

    /**
     * Time-to-live for cached results, expressed as a duration string.
     * Supported units: {@code s} (seconds), {@code m} (minutes),
     * {@code h} (hours), {@code d} (days).  Defaults to {@code "5m"}.
     */
    String ttl() default "5m";

    /**
     * Cache backend to use.  Supported values: {@code "memory"} (Caffeine,
     * default), {@code "redis"}.
     */
    String backend() default "memory";
}
