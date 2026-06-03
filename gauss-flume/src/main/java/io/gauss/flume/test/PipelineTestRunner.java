package io.gauss.flume.test;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.flume.runner.PipelineRunner;
import io.gauss.flume.source.SourceReader;
import io.gauss.flume.source.SourceReaderRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Test-scoped wrapper around {@link PipelineRunner} that supports inline mock
 * source data via a fully isolated (non-global) reader registry.
 *
 * <p>Usage:
 * <pre>{@code
 * PipelineTestRunner runner = new PipelineTestRunner()
 *     .mockSource("file:///data/customers.json", "[{\"id\":1}]")
 *     .register(ChurnPipeline.class);
 *
 * Object result = runner.run("churn-features");
 * assertThat(result)...;
 * }</pre>
 *
 * <p>Mock readers are checked before the global {@link SourceReaderRegistry};
 * unmatched URIs fall through to the global registry, then to the pipeline
 * method body.
 */
public class PipelineTestRunner {

    private final List<SourceReader> mocks    = new ArrayList<>();
    private final PipelineRunner     delegate;

    public PipelineTestRunner() {
        // Build an isolated resolver: mocks first, then global registry
        this.delegate = new PipelineRunner(this::resolve);
    }

    // -------------------------------------------------------------------------
    // Fluent builder methods
    // -------------------------------------------------------------------------

    /**
     * Registers a JSON mock for the given source URI.
     *
     * @param sourceUri exact URI to intercept (must match {@code @Ingest(source=…)})
     * @param json      JSON string to return as the ingest data
     * @return {@code this} for chaining
     */
    public PipelineTestRunner mockSource(String sourceUri, String json) {
        mocks.add(new MockSourceReader(sourceUri, json));
        return this;
    }

    /**
     * Scans and registers a {@code @DataPipeline} class.
     *
     * @param pipelineClass class annotated with {@code @DataPipeline}
     * @return {@code this} for chaining
     */
    public PipelineTestRunner register(Class<?> pipelineClass) {
        delegate.register(pipelineClass);
        return this;
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    /**
     * Runs the named pipeline and returns the output of the last step.
     *
     * @param name pipeline name as declared in {@code @DataPipeline("name")}
     * @return last step output
     */
    public Object run(String name) {
        return delegate.run(name);
    }

    /**
     * Convenience overload: registers and immediately runs the given pipeline class.
     *
     * @param pipelineClass class annotated with {@code @DataPipeline}
     * @return last step output
     */
    public Object run(Class<?> pipelineClass) {
        DataPipeline ann = pipelineClass.getAnnotation(DataPipeline.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    pipelineClass.getName() + " is not annotated with @DataPipeline");
        }
        delegate.register(pipelineClass);
        return delegate.run(ann.value());
    }

    // -------------------------------------------------------------------------
    // Private resolver
    // -------------------------------------------------------------------------

    private Optional<SourceReader> resolve(String sourceUri) {
        // Local mocks take priority over the global registry
        return mocks.stream()
                .filter(r -> r.supports(sourceUri))
                .findFirst()
                .or(() -> SourceReaderRegistry.find(sourceUri));
    }
}
