package io.gauss.flume.scheduler;

/**
 * Immutable descriptor for a scheduled pipeline discovered by
 * {@link ScheduledPipelineRegistry} (HU-013).
 *
 * <p>Combines the pipeline identity (name, hosting class) with the cron
 * schedule that governs its automatic execution.
 *
 * @param pipelineName   the {@link io.gauss.core.annotation.DataPipeline#value()} of
 *                       the hosting class, or {@code "method:pipelineName.methodName"}
 *                       for method-level schedules
 * @param cronExpression raw cron string (5 fields)
 * @param description    human-readable schedule description (may be empty)
 * @param pipelineClass  the Java class annotated with {@code @DataPipeline}
 */
public record ScheduledPipelineDescriptor(
        String   pipelineName,
        String   cronExpression,
        String   description,
        Class<?> pipelineClass
) {

    /** Returns the parsed and validated {@link CronExpression} for this schedule. */
    public CronExpression cron() {
        return CronExpression.parse(cronExpression);
    }
}
