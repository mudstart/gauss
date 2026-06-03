package io.gauss.vigil.experiment;

import java.time.Instant;

/**
 * An immutable snapshot of a single metric observation logged during an
 * experiment run.
 *
 * @param name      metric name (e.g. {@code "auc"}, {@code "loss"})
 * @param value     numeric value
 * @param step      training step / epoch index ({@code -1} if not applicable)
 * @param timestamp wall-clock time when the metric was recorded
 */
public record ExperimentMetric(
        String  name,
        double  value,
        int     step,
        Instant timestamp
) {

    /**
     * Creates a step-less metric recorded at the given timestamp.
     */
    public static ExperimentMetric of(String name, double value, Instant timestamp) {
        return new ExperimentMetric(name, value, -1, timestamp);
    }

    /**
     * Creates a stepped metric recorded at the given timestamp.
     */
    public static ExperimentMetric of(String name, double value, int step, Instant timestamp) {
        return new ExperimentMetric(name, value, step, timestamp);
    }
}
