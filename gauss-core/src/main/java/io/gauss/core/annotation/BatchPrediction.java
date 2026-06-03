package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a prediction method to run asynchronously over a list of inputs,
 * returning a job ID instead of blocking (Augur module, HU-018).
 *
 * <p>The framework:
 * <ol>
 *   <li>Accepts the full input list and immediately returns a {@code JobId}.</li>
 *   <li>Processes inputs in chunks of {@link #batchSize} in a background thread.</li>
 *   <li>Makes progress available for SSE subscription via the generated TypeScript client.</li>
 * </ol>
 *
 * <pre>{@code
 * @MLEndpoint
 * public class ScoringEndpoint {
 *
 *     @BatchPrediction(batchSize = 100)
 *     public double score(CustomerInput input) { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface BatchPrediction {

    /** Maximum number of inputs processed per internal batch chunk. Default: {@code 100}. */
    int batchSize() default 100;
}
