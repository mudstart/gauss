package io.gauss.vigil.experiment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Mutable recording context injected into {@code @Experiment}-annotated methods.
 *
 * <p>Use this object to log metrics and artefacts during a training or
 * evaluation run.  Gauss creates one instance per run invocation and snapshots
 * its contents when the method returns to build the immutable
 * {@link ExperimentRun}.
 *
 * <pre>{@code
 * @Experiment(name = "churn-xgb")
 * public MyModel train(double lr, ExperimentContext ctx) {
 *     MyModel model = fit(lr);
 *     ctx.logMetric("auc", model.auc());
 *     ctx.logMetric("loss", lossValue, step);
 *     ctx.logArtifact("confusion_matrix", model.confusionMatrix());
 *     return model;
 * }
 * }</pre>
 */
public final class ExperimentContext {

    private final List<ExperimentMetric>   metrics   = new ArrayList<>();
    private final List<ExperimentArtifact> artifacts = new ArrayList<>();

    // -------------------------------------------------------------------------
    // Metric logging
    // -------------------------------------------------------------------------

    /**
     * Records a scalar metric at the current wall-clock time (no step).
     *
     * @param name  metric name (e.g. {@code "auc"})
     * @param value numeric value
     */
    public void logMetric(String name, double value) {
        metrics.add(ExperimentMetric.of(name, value, Instant.now()));
    }

    /**
     * Records a scalar metric for a specific training step.
     *
     * @param name  metric name
     * @param value numeric value
     * @param step  training step / epoch index
     */
    public void logMetric(String name, double value, int step) {
        metrics.add(ExperimentMetric.of(name, value, step, Instant.now()));
    }

    // -------------------------------------------------------------------------
    // Artifact logging
    // -------------------------------------------------------------------------

    /**
     * Logs an arbitrary artifact (confusion matrix, chart data, etc.).
     *
     * @param name  logical artifact name
     * @param data  artifact payload
     */
    public void logArtifact(String name, Object data) {
        artifacts.add(ExperimentArtifact.of(name, data, Instant.now()));
    }

    // -------------------------------------------------------------------------
    // Snapshot (package-private — called by ExperimentRunner)
    // -------------------------------------------------------------------------

    /** Returns an unmodifiable view of the recorded metrics. */
    List<ExperimentMetric> metrics() {
        return Collections.unmodifiableList(metrics);
    }

    /** Returns an unmodifiable view of the recorded artifacts. */
    List<ExperimentArtifact> artifacts() {
        return Collections.unmodifiableList(artifacts);
    }
}
