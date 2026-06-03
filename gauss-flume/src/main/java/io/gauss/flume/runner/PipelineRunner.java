package io.gauss.flume.runner;

import io.gauss.flume.model.PipelineDescriptor;
import io.gauss.flume.model.PipelineStep;
import io.gauss.flume.scanner.PipelineScanner;
import io.gauss.flume.source.SourceReader;
import io.gauss.flume.source.SourceReaderRegistry;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Executes registered {@code @DataPipeline} classes by name.
 *
 * <p>Usage:
 * <pre>{@code
 * PipelineRunner runner = new PipelineRunner();
 * runner.register(ChurnPipeline.class);
 * Object result = runner.run("churn-features");
 * }</pre>
 *
 * <p>Each call to {@link #run(String)} creates a fresh instance of the pipeline
 * class, executes the INGEST step, then each TRANSFORM step in topological
 * order, wiring outputs to inputs by raw type matching.
 *
 * <p>The no-arg constructor delegates source resolution to the global
 * {@link SourceReaderRegistry}. A custom resolver can be injected via
 * {@link #PipelineRunner(Function)} — this is used by
 * {@link io.gauss.flume.test.PipelineTestRunner} to achieve test isolation
 * without mutating the global registry.
 */
public class PipelineRunner {

    private final Map<String, PipelineDescriptor> registry = new HashMap<>();

    /** Resolves a source URI to a matching {@link SourceReader} (if any). */
    private final Function<String, Optional<SourceReader>> readerResolver;

    /** Creates a runner that uses the global {@link SourceReaderRegistry}. */
    public PipelineRunner() {
        this(SourceReaderRegistry::find);
    }

    /**
     * Creates a runner with a custom source-reader resolver.
     * Used by test utilities to inject mock data without touching the global registry.
     *
     * @param readerResolver function that returns the best reader for a given source URI
     */
    public PipelineRunner(Function<String, Optional<SourceReader>> readerResolver) {
        this.readerResolver = readerResolver;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Scans and registers a {@code @DataPipeline}-annotated class.
     *
     * @param pipelineClass class to register
     * @throws IllegalArgumentException if the class is not annotated with {@code @DataPipeline}
     */
    public void register(Class<?> pipelineClass) {
        PipelineDescriptor descriptor = PipelineScanner.scan(pipelineClass);
        registry.put(descriptor.name(), descriptor);
    }

    /**
     * Runs the named pipeline and returns the output of the final step.
     *
     * @param name pipeline name as declared in {@code @DataPipeline("name")}
     * @return the value produced by the last step in execution order
     * @throws IllegalArgumentException   if no pipeline with that name is registered
     * @throws PipelineExecutionException if a step fails at runtime
     */
    public Object run(String name) {
        PipelineDescriptor descriptor = registry.get(name);
        if (descriptor == null) {
            throw new IllegalArgumentException(
                    "No pipeline registered with name: '" + name + "'");
        }
        return execute(descriptor);
    }

    /** Returns {@code true} if a pipeline with the given name has been registered. */
    public boolean isRegistered(String name) {
        return registry.containsKey(name);
    }

    // -------------------------------------------------------------------------
    // Private execution logic
    // -------------------------------------------------------------------------

    private Object execute(PipelineDescriptor descriptor) {
        try {
            var constructor = descriptor.pipelineClass().getDeclaredConstructor();
            constructor.setAccessible(true);
            Object instance = constructor.newInstance();

            Map<Class<?>, Object> outputs = new HashMap<>();
            Object lastOutput = null;

            for (PipelineStep step : descriptor.steps()) {
                Object result = invokeStep(instance, step, outputs);
                if (step.outputType() != null && step.outputType() != void.class) {
                    outputs.put(step.outputType(), result);
                }
                lastOutput = result;
            }

            return lastOutput;

        } catch (ReflectiveOperationException e) {
            throw new PipelineExecutionException(
                    "Failed to instantiate pipeline '" + descriptor.name() + "'", e);
        }
    }

    private Object invokeStep(Object instance,
                               PipelineStep step,
                               Map<Class<?>, Object> outputs)
            throws PipelineExecutionException {
        Method method = step.method();
        method.setAccessible(true);

        try {
            if (step.isIngest()) {
                // Try the injected resolver first
                Optional<SourceReader> reader = readerResolver.apply(step.source());
                if (reader.isPresent()) {
                    try {
                        return reader.get().read(step.source(), method.getGenericReturnType());
                    } catch (IOException e) {
                        throw new PipelineExecutionException(
                                "SourceReader failed for step '" + step.name() +
                                "' (source: " + step.source() + ")", e);
                    }
                }
                // Fall back to method body (developer-provided ingestion logic)
                return method.invoke(instance);
            }

            // Resolve parameters from previously produced outputs
            Class<?>[] paramTypes = method.getParameterTypes();
            Object[]   args       = new Object[paramTypes.length];

            for (int i = 0; i < paramTypes.length; i++) {
                if (!outputs.containsKey(paramTypes[i])) {
                    throw new PipelineExecutionException(
                            "Step '" + step.name() + "' requires type " +
                            paramTypes[i].getName() + " but no preceding step produces it");
                }
                args[i] = outputs.get(paramTypes[i]);
            }

            return method.invoke(instance, args);

        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new PipelineExecutionException(
                    "Step '" + step.name() + "' threw an exception", cause);
        } catch (IllegalAccessException e) {
            throw new PipelineExecutionException(
                    "Cannot access step method '" + step.name() + "'", e);
        }
    }
}
