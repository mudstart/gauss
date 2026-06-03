package io.gauss.vigil.rollback;

import java.time.Instant;

/**
 * Immutable record of an automatic model rollback (HU-054).
 *
 * <p>Emitted by {@link RollbackService} whenever a model is reverted to its
 * previous production version.  The event is also written to the audit log
 * so that governance teams can trace every unattended production change.
 *
 * @param modelId       the model registration ID that was rolled back
 * @param modelName     human-readable model name
 * @param metric        the metric that triggered the rollback
 * @param metricValue   the observed value that exceeded the threshold
 * @param threshold     the configured threshold that was breached
 * @param previousId    the registration ID that was promoted back to production
 * @param triggeredAt   wall-clock time of the rollback
 */
public record RollbackEvent(
        String  modelId,
        String  modelName,
        String  metric,
        double  metricValue,
        double  threshold,
        String  previousId,
        Instant triggeredAt
) {

    /** Short human-readable summary suitable for a log line. */
    public String summary() {
        return String.format(
                "AUTO-ROLLBACK: model=%s metric=%s value=%.4f threshold=%.4f previous=%s",
                modelName, metric, metricValue, threshold, previousId);
    }
}
