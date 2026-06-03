package io.gauss.spark.config;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.SparkExecution;

import java.util.Optional;

/**
 * Captures Spark execution metadata for a {@link DataPipeline} class (HU-015).
 *
 * <p>A descriptor is created by {@link #scan(Class)} at application startup.
 * If the class has no {@link SparkExecution} annotation, {@link #hasSparkExecution()}
 * returns {@code false} and the pipeline falls back to local JVM execution.
 *
 * @param pipelineName  the {@link DataPipeline#value()} identifier
 * @param pipelineClass the annotated class
 * @param config        Spark configuration, or empty for local-only pipelines
 */
public record SparkExecutionDescriptor(
        String             pipelineName,
        Class<?>           pipelineClass,
        Optional<SparkConfig> config
) {

    // -------------------------------------------------------------------------

    /**
     * Scans {@code pipelineClass} and builds a descriptor.
     *
     * @param pipelineClass must be annotated with {@link DataPipeline}
     * @return descriptor (Spark config is empty if no {@link SparkExecution})
     * @throws IllegalArgumentException if the class has no {@link DataPipeline}
     */
    public static SparkExecutionDescriptor scan(Class<?> pipelineClass) {
        DataPipeline dp = pipelineClass.getAnnotation(DataPipeline.class);
        if (dp == null) throw new IllegalArgumentException(
                pipelineClass.getSimpleName() + " has no @DataPipeline annotation");

        SparkExecution se = pipelineClass.getAnnotation(SparkExecution.class);
        Optional<SparkConfig> config = se == null
                ? Optional.empty()
                : Optional.of(SparkConfig.from(pipelineClass));

        return new SparkExecutionDescriptor(dp.value(), pipelineClass, config);
    }

    // -------------------------------------------------------------------------

    /** Returns {@code true} if the pipeline is configured for Spark execution. */
    public boolean hasSparkExecution() {
        return config.isPresent();
    }

    /**
     * Returns the Spark configuration if present, or {@link SparkConfig#local()}
     * as a safe fallback for local runs.
     */
    public SparkConfig effectiveConfig() {
        return config.orElseGet(SparkConfig::local);
    }
}
