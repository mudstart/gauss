package io.gauss.flume.scheduler;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Scheduled;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Scans pipeline classes for {@link Scheduled} annotations and produces
 * {@link ScheduledPipelineDescriptor} records (Flume module, HU-013).
 *
 * <p>Two scheduling modes are supported:
 * <ol>
 *   <li><b>Class-level</b> — the whole pipeline is scheduled when both
 *       {@link DataPipeline} and {@link Scheduled} are present on the class.</li>
 *   <li><b>Method-level</b> — an individual method inside a {@code @DataPipeline}
 *       class is scheduled when it carries {@code @Scheduled}.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * List<ScheduledPipelineDescriptor> schedules =
 *         ScheduledPipelineRegistry.scan(ChurnPipeline.class, NlpPipeline.class);
 * }</pre>
 */
public final class ScheduledPipelineRegistry {

    private ScheduledPipelineRegistry() {}

    // -------------------------------------------------------------------------
    // Scanning
    // -------------------------------------------------------------------------

    /**
     * Scans the given classes and returns one {@link ScheduledPipelineDescriptor}
     * for every scheduled entry point found.
     *
     * @param pipelineClasses classes to inspect
     * @return unmodifiable list of descriptors
     * @throws IllegalArgumentException if a cron expression is invalid
     */
    public static List<ScheduledPipelineDescriptor> scan(Class<?>... pipelineClasses) {
        List<ScheduledPipelineDescriptor> results = new ArrayList<>();
        for (Class<?> cls : pipelineClasses) {
            results.addAll(scanClass(cls));
        }
        return List.copyOf(results);
    }

    /**
     * Scans a single class and returns all schedule descriptors found on it.
     *
     * @param cls the class to scan
     * @return list of descriptors (may be empty)
     */
    public static List<ScheduledPipelineDescriptor> scanClass(Class<?> cls) {
        List<ScheduledPipelineDescriptor> results = new ArrayList<>();

        DataPipeline pipeline = cls.getAnnotation(DataPipeline.class);
        Scheduled    classSchedule = cls.getAnnotation(Scheduled.class);

        // Class-level schedule — the whole pipeline
        if (pipeline != null && classSchedule != null) {
            CronExpression.validate(classSchedule.cron());  // eager validation
            results.add(new ScheduledPipelineDescriptor(
                    pipeline.value(),
                    classSchedule.cron(),
                    classSchedule.description(),
                    cls));
        }

        // Method-level schedules inside a @DataPipeline class
        if (pipeline != null) {
            for (Method method : cls.getDeclaredMethods()) {
                Scheduled methodSchedule = method.getAnnotation(Scheduled.class);
                if (methodSchedule != null) {
                    CronExpression.validate(methodSchedule.cron());
                    results.add(new ScheduledPipelineDescriptor(
                            "method:" + pipeline.value() + "." + method.getName(),
                            methodSchedule.cron(),
                            methodSchedule.description(),
                            cls));
                }
            }
        }

        return List.copyOf(results);
    }

    /**
     * Returns the first descriptor whose {@link ScheduledPipelineDescriptor#pipelineName()}
     * equals {@code name}, if one exists.
     */
    public static Optional<ScheduledPipelineDescriptor> findByName(
            List<ScheduledPipelineDescriptor> descriptors, String name) {
        return descriptors.stream()
                .filter(d -> d.pipelineName().equals(name))
                .findFirst();
    }
}
