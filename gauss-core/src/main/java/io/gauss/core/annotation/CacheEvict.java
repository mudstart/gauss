package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Invalidates prediction cache entries when the annotated method is invoked
 * (Augur module, HU-021).
 *
 * <p>Placing this annotation on a method signals the framework to clear the
 * cache for the named endpoint after the method completes.  If {@link #cacheName}
 * is blank the cache for the enclosing {@link MLEndpoint} class is evicted.
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ChurnEndpoint {
 *
 *     @CachedPrediction(ttl = "5m")
 *     public double predict(CustomerInput input) { ... }
 *
 *     @CacheEvict
 *     public void refreshModel() { ... }   // clears all ChurnEndpoint predictions
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CacheEvict {

    /**
     * Name of the cache (i.e., the endpoint name) to evict.
     * If blank, the enclosing endpoint's cache is cleared.
     */
    String cacheName() default "";
}
