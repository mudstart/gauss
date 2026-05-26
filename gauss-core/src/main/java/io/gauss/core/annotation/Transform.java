package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a transformation step within a {@link DataPipeline}.
 *
 * <p>The framework automatically wires the method's parameters by matching
 * their types against the outputs of other pipeline steps. If a parameter
 * type matches the return type of an {@link Ingest} or another
 * {@code @Transform} method, it is injected automatically.
 *
 * <pre>{@code
 * @Transform
 * public Dataset<EnrichedTx> enrich(Dataset<Transaction> raw,
 *                                   Dataset<Label> labels) { ... }
 *
 * @Transform
 * public Dataset<Features> featureEngineer(Dataset<EnrichedTx> enriched) { ... }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transform {

    /** Human-readable step name for monitoring and lineage. Defaults to method name. */
    String value() default "";
}
