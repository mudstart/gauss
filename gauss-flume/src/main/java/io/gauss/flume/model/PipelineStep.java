package io.gauss.flume.model;

import java.lang.reflect.Method;

/**
 * Represents one step inside a {@code @DataPipeline} class — either an
 * {@code @Ingest} entry-point or a {@code @Transform} transformation.
 *
 * @param name       human-readable step name (annotation value or method name)
 * @param method     the annotated Java method
 * @param type       INGEST or TRANSFORM
 * @param source     source URI for INGEST steps (empty for TRANSFORM)
 * @param inputType  the Java type this step expects as input (null for INGEST)
 * @param outputType the Java type this step produces
 */
public record PipelineStep(
        String name,
        Method method,
        StepType type,
        String source,
        Class<?> inputType,
        Class<?> outputType
) {
    public boolean isIngest()    { return type == StepType.INGEST; }
    public boolean isTransform() { return type == StepType.TRANSFORM; }
}
