package io.gauss.vigil.experiment;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable snapshot of a completed (or failed) experiment run.
 *
 * <p>Instances are created by {@link ExperimentRunner} and persisted in the
 * configured {@link ExperimentStore}.
 *
 * @param id              unique run identifier (UUID)
 * @param experimentName  group name from {@link io.gauss.core.annotation.Experiment}
 * @param tags            free-form tags for filtering
 * @param params          method parameters captured at invocation time
 * @param metrics         time-stamped metric observations logged via
 *                        {@link ExperimentContext#logMetric}
 * @param artifacts       arbitrary artefacts logged via
 *                        {@link ExperimentContext#logArtifact}
 * @param returnValue     the value returned by the annotated method, or empty
 *                        for {@code void} methods or failed runs
 * @param status          final lifecycle state of this run
 * @param startedAt       wall-clock time when execution began
 * @param finishedAt      wall-clock time when execution ended
 * @param failureCause    populated only when {@code status == FAILED}
 */
public record ExperimentRun(
        String                   id,
        String                   experimentName,
        String[]                 tags,
        Map<String, Object>      params,
        List<ExperimentMetric>   metrics,
        List<ExperimentArtifact> artifacts,
        Optional<Object>         returnValue,
        RunStatus                status,
        Instant                  startedAt,
        Instant                  finishedAt,
        Optional<Throwable>      failureCause
) {

    // -------------------------------------------------------------------------
    // Factory helpers (called by ExperimentRunner)
    // -------------------------------------------------------------------------

    static ExperimentRun completed(String id,
                                    String name,
                                    String[] tags,
                                    Map<String, Object> params,
                                    Instant startedAt,
                                    Instant finishedAt,
                                    ExperimentContext ctx,
                                    Object returnValue) {
        return new ExperimentRun(
                id, name, tags, params,
                ctx.metrics(), ctx.artifacts(),
                Optional.ofNullable(returnValue),
                RunStatus.COMPLETED,
                startedAt, finishedAt,
                Optional.empty());
    }

    static ExperimentRun failed(String id,
                                 String name,
                                 String[] tags,
                                 Map<String, Object> params,
                                 Instant startedAt,
                                 Instant finishedAt,
                                 ExperimentContext ctx,
                                 Throwable cause) {
        return new ExperimentRun(
                id, name, tags, params,
                ctx.metrics(), ctx.artifacts(),
                Optional.empty(),
                RunStatus.FAILED,
                startedAt, finishedAt,
                Optional.of(cause));
    }

    // -------------------------------------------------------------------------
    // Convenience helpers
    // -------------------------------------------------------------------------

    /**
     * Returns the last recorded value of the named metric, or empty if the
     * metric was never logged in this run.
     */
    public Optional<Double> latestMetric(String metricName) {
        return metrics.stream()
                .filter(m -> m.name().equals(metricName))
                .reduce((first, second) -> second)  // last wins
                .map(ExperimentMetric::value);
    }

    /**
     * Returns {@code true} if this run finished successfully.
     */
    public boolean isCompleted() {
        return status == RunStatus.COMPLETED;
    }
}
