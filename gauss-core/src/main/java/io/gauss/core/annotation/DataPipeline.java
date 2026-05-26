package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a class as a Gauss data pipeline (Flume module).
 *
 * <p>Methods within the class are decorated with {@link Ingest} and
 * {@link Transform} to define the pipeline steps. The framework infers
 * the execution order from the dependency graph between methods.
 *
 * <pre>{@code
 * @DataPipeline("churn-features")
 * public class ChurnPipeline {
 *
 *     @Ingest(source = "jdbc://datasource/customers")
 *     public Dataset<Customer> loadCustomers() { ... }
 *
 *     @Transform
 *     public Dataset<ChurnFeatures> engineer(Dataset<Customer> customers) { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DataPipeline {

    /** Unique pipeline name used for scheduling, monitoring and registry lookups. */
    String value();

    /** Human-readable description shown in the admin UI. */
    String description() default "";
}
