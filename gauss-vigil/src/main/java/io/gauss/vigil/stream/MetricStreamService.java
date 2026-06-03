package io.gauss.vigil.stream;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Buffers per-step metric observations for real-time SSE streaming to the
 * training dashboard (HU-024).
 *
 * <p>Callers log metrics during training via {@link #log}; a REST SSE endpoint
 * polls {@link #since} to retrieve new observations since the last known step,
 * providing live chart updates without long-polling.
 *
 * <p>Usage:
 * <pre>{@code
 * MetricStreamService stream = new MetricStreamService();
 *
 * // Inside @Experiment method:
 * for (int epoch = 0; epoch < 100; epoch++) {
 *     double loss = train(epoch);
 *     stream.log(experimentId, "loss", loss, epoch);
 * }
 *
 * // SSE endpoint polls:
 * List<StepMetric> newPoints = stream.since(experimentId, "loss", lastStep);
 * }</pre>
 */
public final class MetricStreamService {

    // experimentId → metricName → ordered list of observations
    private final Map<String, Map<String, CopyOnWriteArrayList<StepMetric>>> store =
            new ConcurrentHashMap<>();

    private final Supplier<Instant> clock;

    public MetricStreamService() { this(Instant::now); }

    public MetricStreamService(Supplier<Instant> clock) { this.clock = clock; }

    // -------------------------------------------------------------------------

    /**
     * Records a metric observation for the given experiment and step.
     *
     * @param experimentId the run ID
     * @param metricName   metric label
     * @param value        observed value
     * @param step         training step index (should be monotonically increasing)
     */
    public void log(String experimentId, String metricName, double value, int step) {
        store.computeIfAbsent(experimentId, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(metricName,   k -> new CopyOnWriteArrayList<>())
             .add(new StepMetric(experimentId, metricName, value, step, clock.get()));
    }

    /**
     * Returns all observations for a metric in step order.
     */
    public List<StepMetric> all(String experimentId, String metricName) {
        Map<String, CopyOnWriteArrayList<StepMetric>> byMetric = store.get(experimentId);
        if (byMetric == null) return List.of();
        CopyOnWriteArrayList<StepMetric> list = byMetric.get(metricName);
        return list == null ? List.of() : List.copyOf(list);
    }

    /**
     * Returns observations whose {@code step} is strictly greater than
     * {@code fromStep}.  This is the SSE polling method.
     *
     * @param experimentId the run to query
     * @param metricName   the metric to stream
     * @param fromStep     the last step already delivered (exclusive lower bound)
     */
    public List<StepMetric> since(String experimentId, String metricName, int fromStep) {
        return all(experimentId, metricName).stream()
                .filter(m -> m.step() > fromStep)
                .toList();
    }

    /**
     * Returns the set of distinct metric names recorded for an experiment.
     */
    public java.util.Set<String> metricNames(String experimentId) {
        Map<String, CopyOnWriteArrayList<StepMetric>> byMetric = store.get(experimentId);
        return byMetric == null ? java.util.Set.of() : java.util.Set.copyOf(byMetric.keySet());
    }

    /** Total observations recorded for a metric. */
    public int count(String experimentId, String metricName) {
        return all(experimentId, metricName).size();
    }

    /** Clears all buffered data (for tests or after experiment completion). */
    public void clear(String experimentId) {
        store.remove(experimentId);
    }
}
