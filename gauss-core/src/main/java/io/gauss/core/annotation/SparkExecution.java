package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Configures a {@link DataPipeline} class to execute on Apache Spark rather
 * than the local JVM thread pool (gauss-spark module, HU-015).
 *
 * <p>When absent the pipeline runs locally (single JVM, in-memory).
 * When present, the {@code gauss-spark} module bootstraps a
 * {@code SparkSession} using the configured parameters before delegating
 * each {@code @Ingest} and {@code @Transform} step to Spark.
 *
 * <p>Requires the optional {@code gauss-spark} module on the classpath.
 *
 * <pre>{@code
 * @SparkExecution(master = "spark://cluster:7077", appName = "churn-etl")
 * @DataPipeline("churn-features")
 * public class ChurnFeaturePipeline {
 *
 *     @Ingest(source = "jdbc://warehouse/customers")
 *     public Dataset<Customer> load() { ... }
 *
 *     @Transform
 *     public Dataset<ChurnFeature> engineer(Dataset<Customer> data) { ... }
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SparkExecution {

    /**
     * Spark master URL.  Examples:
     * <ul>
     *   <li>{@code "local[*]"} — local mode, all CPUs (default)</li>
     *   <li>{@code "local[4]"} — local mode, 4 threads</li>
     *   <li>{@code "spark://host:7077"} — standalone cluster</li>
     *   <li>{@code "yarn"} — YARN cluster</li>
     *   <li>{@code "k8s://https://k8s-api:6443"} — Kubernetes</li>
     * </ul>
     */
    String master() default "local[*]";

    /**
     * Human-readable application name shown in the Spark UI.
     * Defaults to the pipeline name from {@link DataPipeline#value()}.
     */
    String appName() default "";

    /**
     * Executor memory per worker node (e.g., {@code "2g"}, {@code "512m"}).
     * Defaults to {@code "1g"}.
     */
    String executorMemory() default "1g";

    /**
     * Number of CPU cores to allocate per executor.
     * Defaults to {@code 2}.
     */
    int executorCores() default 2;

    /**
     * If {@code true} (default), enables Spark's Adaptive Query Execution
     * (AQE) for automatic optimisation of join strategies and partition counts.
     */
    boolean adaptiveQueryExecution() default true;
}
