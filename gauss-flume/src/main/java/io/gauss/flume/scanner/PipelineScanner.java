package io.gauss.flume.scanner;

import io.gauss.core.annotation.DataPipeline;
import io.gauss.core.annotation.Ingest;
import io.gauss.core.annotation.Transform;
import io.gauss.flume.model.PipelineDescriptor;
import io.gauss.flume.model.PipelineStep;
import io.gauss.flume.model.StepType;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Scans a {@code @DataPipeline}-annotated class and produces a fully resolved
 * {@link PipelineDescriptor} with steps sorted in topological execution order.
 *
 * <p>Dependency resolution works by matching the <em>raw return type</em> of
 * each step against the <em>raw parameter types</em> of downstream transforms.
 * Because Java erases generic type arguments at runtime, pipeline steps should
 * use distinct concrete types (or distinct wrapper types) to ensure unambiguous
 * wiring.
 */
public final class PipelineScanner {

    private PipelineScanner() {}

    /**
     * Scans {@code pipelineClass} and returns a fully resolved descriptor.
     *
     * @param pipelineClass class annotated with {@code @DataPipeline}
     * @return ordered descriptor ready for execution
     * @throws IllegalArgumentException if the class lacks {@code @DataPipeline}
     * @throws IllegalStateException    if a dependency cycle is detected
     */
    public static PipelineDescriptor scan(Class<?> pipelineClass) {
        DataPipeline annotation = pipelineClass.getAnnotation(DataPipeline.class);
        if (annotation == null) {
            throw new IllegalArgumentException(
                    "Class " + pipelineClass.getName() +
                    " is not annotated with @DataPipeline");
        }

        List<PipelineStep> raw   = collectSteps(pipelineClass);
        List<PipelineStep> ordered = topologicalSort(raw);

        return new PipelineDescriptor(annotation.value(), pipelineClass, ordered);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static List<PipelineStep> collectSteps(Class<?> pipelineClass) {
        List<PipelineStep> steps = new ArrayList<>();

        for (Method method : pipelineClass.getDeclaredMethods()) {
            Ingest    ingest    = method.getAnnotation(Ingest.class);
            Transform transform = method.getAnnotation(Transform.class);

            if (ingest != null) {
                steps.add(new PipelineStep(
                        method.getName(),
                        method,
                        StepType.INGEST,
                        ingest.source(),
                        null,                      // INGEST has no pipeline input
                        method.getReturnType()
                ));
            } else if (transform != null) {
                String stepName = transform.value().isBlank()
                        ? method.getName()
                        : transform.value();
                Class<?> primaryInput = method.getParameterCount() > 0
                        ? method.getParameterTypes()[0]
                        : null;
                steps.add(new PipelineStep(
                        stepName,
                        method,
                        StepType.TRANSFORM,
                        "",
                        primaryInput,
                        method.getReturnType()
                ));
            }
        }
        return steps;
    }

    /**
     * DFS-based topological sort. Each transform declares its dependencies via
     * parameter types; the INGEST step(s) have no dependencies and always
     * come first in the resulting order.
     *
     * <p><b>Producer-map priority rule:</b> INGEST steps override TRANSFORM steps
     * for the same output type.  This handles the common case where both an ingest
     * and a downstream transform happen to produce the same raw type (e.g.
     * {@code String}): the transform's parameter is wired to the ingest output,
     * not to itself.
     */
    private static List<PipelineStep> topologicalSort(List<PipelineStep> steps) {
        // Build producer map with INGEST priority:
        //   1. TRANSFORM steps added first
        //   2. INGEST steps added second — they overwrite any TRANSFORM entry for the same type
        Map<Class<?>, PipelineStep> producers = new HashMap<>();
        for (PipelineStep step : steps) {
            if (step.isTransform() && step.outputType() != null
                    && step.outputType() != void.class) {
                producers.put(step.outputType(), step);
            }
        }
        for (PipelineStep step : steps) {
            if (step.isIngest() && step.outputType() != null
                    && step.outputType() != void.class) {
                producers.put(step.outputType(), step);
            }
        }

        // Build dependency edges: step → steps it depends on (by param types)
        // A step is never wired to itself even if the type matches.
        Map<PipelineStep, List<PipelineStep>> deps = new IdentityHashMap<>();
        for (PipelineStep step : steps) {
            List<PipelineStep> stepDeps = new ArrayList<>();
            if (step.isTransform()) {
                for (Class<?> paramType : step.method().getParameterTypes()) {
                    PipelineStep producer = producers.get(paramType);
                    if (producer != null && producer != step) {
                        stepDeps.add(producer);
                    }
                }
            }
            deps.put(step, stepDeps);
        }

        // DFS topological sort (post-order = dependency before dependent)
        LinkedHashSet<PipelineStep> sorted   = new LinkedHashSet<>();
        Set<PipelineStep>           visiting = new HashSet<>();

        for (PipelineStep step : steps) {
            dfsVisit(step, deps, sorted, visiting);
        }

        return new ArrayList<>(sorted);
    }

    private static void dfsVisit(PipelineStep step,
                                  Map<PipelineStep, List<PipelineStep>> deps,
                                  LinkedHashSet<PipelineStep> sorted,
                                  Set<PipelineStep> visiting) {
        if (sorted.contains(step))   return;
        if (visiting.contains(step)) {
            throw new IllegalStateException(
                    "Cycle detected in pipeline at step: '" + step.name() + "'");
        }
        visiting.add(step);
        for (PipelineStep dep : deps.get(step)) {
            dfsVisit(dep, deps, sorted, visiting);
        }
        visiting.remove(step);
        sorted.add(step);
    }
}
