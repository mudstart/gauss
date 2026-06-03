package io.gauss.vigil.stream;

import java.time.Instant;

/**
 * A single metric observation at a specific training step (HU-024).
 *
 * @param experimentId  the run this observation belongs to
 * @param metricName    name of the metric (e.g., {@code "loss"}, {@code "auc"})
 * @param value         metric value at this step
 * @param step          training step index (0-based)
 * @param recordedAt    wall-clock timestamp
 */
public record StepMetric(
        String  experimentId,
        String  metricName,
        double  value,
        int     step,
        Instant recordedAt
) {}
