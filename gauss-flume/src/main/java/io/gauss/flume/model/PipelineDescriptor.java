package io.gauss.flume.model;

import java.util.List;

/**
 * Fully resolved description of a {@code @DataPipeline} class,
 * with steps ordered for sequential execution.
 *
 * @param name        value from {@code @DataPipeline("name")}
 * @param pipelineClass the annotated class
 * @param steps       topologically-ordered steps (INGEST first, then TRANSFORMs)
 */
public record PipelineDescriptor(
        String name,
        Class<?> pipelineClass,
        List<PipelineStep> steps
) {
    /** Returns the single INGEST step, or throws if none found. */
    public PipelineStep ingestStep() {
        return steps.stream()
                .filter(PipelineStep::isIngest)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Pipeline '" + name + "' has no @Ingest method"));
    }

    /** Returns all TRANSFORM steps in execution order. */
    public List<PipelineStep> transformSteps() {
        return steps.stream().filter(PipelineStep::isTransform).toList();
    }
}
