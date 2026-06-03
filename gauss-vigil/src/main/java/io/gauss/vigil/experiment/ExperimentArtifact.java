package io.gauss.vigil.experiment;

import java.time.Instant;

/**
 * An immutable snapshot of an artifact logged during an experiment run.
 *
 * <p>Artifacts are arbitrary objects associated with a run — for example a
 * confusion matrix, a serialised model, or an evaluation report.
 *
 * @param name        logical artifact name (e.g. {@code "confusion_matrix"})
 * @param data        the artifact payload (caller-defined object)
 * @param capturedAt  wall-clock time when the artifact was logged
 */
public record ExperimentArtifact(
        String  name,
        Object  data,
        Instant capturedAt
) {

    /**
     * Creates an artifact with the given name, data and capture timestamp.
     */
    public static ExperimentArtifact of(String name, Object data, Instant capturedAt) {
        return new ExperimentArtifact(name, data, capturedAt);
    }
}
