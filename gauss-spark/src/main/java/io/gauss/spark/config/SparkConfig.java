package io.gauss.spark.config;

import io.gauss.core.annotation.SparkExecution;
import io.gauss.core.annotation.DataPipeline;

/**
 * Immutable Spark session configuration built from a {@link SparkExecution}
 * annotation (gauss-spark module, HU-015).
 *
 * @param master                 Spark master URL
 * @param appName                application name shown in Spark UI
 * @param executorMemory         memory per executor (e.g., {@code "2g"})
 * @param executorCores          CPU cores per executor
 * @param adaptiveQueryExecution whether AQE is enabled
 */
public record SparkConfig(
        String  master,
        String  appName,
        String  executorMemory,
        int     executorCores,
        boolean adaptiveQueryExecution
) {

    /**
     * Builds a {@code SparkConfig} from a {@link SparkExecution} annotation on
     * {@code pipelineClass}.  If {@link SparkExecution#appName()} is blank, the
     * pipeline name from {@link DataPipeline#value()} is used instead.
     *
     * @param pipelineClass the class annotated with {@code @SparkExecution}
     * @return parsed config
     * @throws IllegalArgumentException if no {@link SparkExecution} is present
     */
    public static SparkConfig from(Class<?> pipelineClass) {
        SparkExecution ann = pipelineClass.getAnnotation(SparkExecution.class);
        if (ann == null) throw new IllegalArgumentException(
                pipelineClass.getSimpleName() + " has no @SparkExecution annotation");

        String name = ann.appName().isBlank()
                ? pipelineName(pipelineClass)
                : ann.appName();

        return new SparkConfig(ann.master(), name,
                ann.executorMemory(), ann.executorCores(),
                ann.adaptiveQueryExecution());
    }

    /** Creates a config for local single-JVM execution (no cluster required). */
    public static SparkConfig local() {
        return new SparkConfig("local[*]", "gauss-local", "1g", 1, true);
    }

    /** Creates a config for local execution with a specific thread count. */
    public static SparkConfig local(int threads) {
        return new SparkConfig("local[" + threads + "]", "gauss-local",
                "1g", 1, true);
    }

    /** Returns {@code true} if this is a local execution mode. */
    public boolean isLocal() {
        return master.startsWith("local");
    }

    // -------------------------------------------------------------------------

    private static String pipelineName(Class<?> cls) {
        DataPipeline dp = cls.getAnnotation(DataPipeline.class);
        return dp != null ? dp.value() : cls.getSimpleName();
    }
}
