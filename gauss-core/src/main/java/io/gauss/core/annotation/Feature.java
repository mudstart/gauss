package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a method as a feature definition in the Gauss feature store (Stratum module).
 *
 * <p>The method defines how the feature is computed from an entity ID.
 * Stratum caches the result for {@link #ttl()} and guarantees the same
 * computation logic is used for both offline materialisation and online serving.
 *
 * <pre>{@code
 * @Feature(ttl = "1h", description = "30-day transaction count for a customer")
 * public int txCount30d(String customerId) {
 *     return txRepository.countByCustomer(customerId,
 *             LocalDate.now().minusDays(30));
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Feature {

    /** ISO-8601 duration for cache TTL (e.g. {@code "1h"}, {@code "30m"}, {@code "7d"}). */
    String ttl() default "1h";

    /** Human-readable description shown in the feature catalogue UI. */
    String description() default "";

    /** Feature version, incremented when computation logic changes. */
    int version() default 1;
}
