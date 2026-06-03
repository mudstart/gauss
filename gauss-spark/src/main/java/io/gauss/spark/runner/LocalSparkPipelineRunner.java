package io.gauss.spark.runner;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import io.gauss.spark.config.SparkConfig;
import io.gauss.spark.config.SparkExecutionDescriptor;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Executes a {@link DataPipeline} locally in the calling JVM thread,
 * regardless of whether a Spark cluster is available (gauss-spark module, HU-015).
 *
 * <p>This runner is the fallback path when {@code @SparkExecution} is present
 * but no Spark runtime is detected on the classpath, and it is also the
 * primary runner for unit tests that validate pipeline logic without a cluster.
 *
 * <p>Usage:
 * <pre>{@code
 * LocalSparkPipelineRunner runner = new LocalSparkPipelineRunner();
 *
 * @DataPipeline("etl")
 * @SparkExecution(master = "local[2]")
 * class EtlPipeline {
 *     @Ingest(source = "memory://data")
 *     public List<String> load() { return List.of("a", "b"); }
 *
 *     @Transform
 *     public List<String> upper(List<String> data) {
 *         return data.stream().map(String::toUpperCase).toList();
 *     }
 * }
 *
 * SparkJobResult result = runner.run(new EtlPipeline());
 * }</pre>
 */
public final class LocalSparkPipelineRunner {

    private static final Logger LOG = Logger.getLogger(LocalSparkPipelineRunner.class.getName());

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Executes the pipeline represented by {@code pipelineBean}, using the
     * {@link SparkExecutionDescriptor} discovered on its class.
     *
     * @param pipelineBean an instance of a {@link DataPipeline}-annotated class
     * @return job result with timing and record counts
     * @throws IllegalArgumentException if the class has no {@link DataPipeline}
     * @throws RuntimeException         wrapping any step-execution error
     */
    public SparkJobResult run(Object pipelineBean) {
        Class<?> cls = pipelineBean.getClass();
        SparkExecutionDescriptor desc = SparkExecutionDescriptor.scan(cls);
        SparkConfig config = desc.effectiveConfig();

        LOG.info(() -> String.format("LocalSparkPipelineRunner: executing '%s' with config %s",
                desc.pipelineName(), config));

        Instant start = Instant.now();
        Map<String, Object> outputs = executeSteps(pipelineBean, cls);
        Duration elapsed = Duration.between(start, Instant.now());

        long recordsRead    = countRecords(outputs, cls, Ingest.class);
        long recordsWritten = countLastOutput(outputs);

        SparkJobResult result = new SparkJobResult(
                desc.pipelineName(), recordsRead, recordsWritten, elapsed, true);
        LOG.info(result::summary);
        return result;
    }

    // -------------------------------------------------------------------------
    // Step execution (topological order via annotation scanning)
    // -------------------------------------------------------------------------

    private Map<String, Object> executeSteps(Object bean, Class<?> cls) {
        Map<String, Object> context = new LinkedHashMap<>();

        // Phase 1: execute @Ingest methods
        for (Method m : cls.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Ingest.class)) {
                m.setAccessible(true);
                try {
                    Object result = m.invoke(bean);
                    context.put(m.getName(), result);
                } catch (Exception e) {
                    throw new RuntimeException("@Ingest step '" + m.getName() + "' failed", e);
                }
            }
        }

        // Phase 2: execute @Transform methods in declaration order
        List<Method> transforms = new ArrayList<>();
        for (Method m : cls.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Transform.class)) transforms.add(m);
        }

        for (Method m : transforms) {
            m.setAccessible(true);
            Object[] args = resolveArgs(m, context);
            try {
                Object result = m.invoke(bean, args);
                context.put(m.getName(), result);
            } catch (Exception e) {
                throw new RuntimeException("@Transform step '" + m.getName() + "' failed", e);
            }
        }

        return context;
    }

    /** Resolves method parameters from the execution context by type matching. */
    private Object[] resolveArgs(Method m, Map<String, Object> context) {
        Class<?>[] params = m.getParameterTypes();
        Object[] args = new Object[params.length];
        List<Object> available = new ArrayList<>(context.values());
        for (int i = 0; i < params.length; i++) {
            for (Object v : available) {
                if (v != null && params[i].isAssignableFrom(v.getClass())) {
                    args[i] = v;
                    break;
                }
            }
        }
        return args;
    }

    /** Counts records by inspecting List outputs from @Ingest steps. */
    private long countRecords(Map<String, Object> outputs, Class<?> cls,
                               Class<?> annotationType) {
        long count = 0;
        for (Method m : cls.getDeclaredMethods()) {
            if (m.isAnnotationPresent(Ingest.class)) {
                Object v = outputs.get(m.getName());
                count += sizeOf(v);
            }
        }
        return count;
    }

    /** Counts records in the last output value in the context. */
    private long countLastOutput(Map<String, Object> outputs) {
        Object last = null;
        for (Object v : outputs.values()) last = v;
        return sizeOf(last);
    }

    private long sizeOf(Object v) {
        if (v instanceof java.util.Collection<?> c) return c.size();
        if (v instanceof Object[] a) return a.length;
        return v != null ? 1 : 0;
    }
}
