package io.gauss.augur.slo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Collects latency samples (in milliseconds) and computes percentile statistics
 * for SLO verification (Augur module, HU-042).
 *
 * <p>Usage:
 * <pre>{@code
 * LatencyStats stats = new LatencyStats();
 * for (long latencyMs : measurements) {
 *     stats.record(latencyMs);
 * }
 * System.out.printf("p99 = %d ms%n", stats.p99());
 * }</pre>
 */
public final class LatencyStats {

    private final List<Long> samples = new ArrayList<>();
    private boolean sorted = true;

    // -------------------------------------------------------------------------
    // Recording
    // -------------------------------------------------------------------------

    /**
     * Records a single latency observation in milliseconds.
     *
     * @param millis latency in ms (must be &ge; 0)
     */
    public void record(long millis) {
        if (millis < 0) throw new IllegalArgumentException("Latency must be non-negative: " + millis);
        samples.add(millis);
        sorted = false;
    }

    /** Records all values from an iterable. */
    public void recordAll(Iterable<Long> values) {
        values.forEach(this::record);
    }

    // -------------------------------------------------------------------------
    // Statistics
    // -------------------------------------------------------------------------

    /** Number of recorded samples. */
    public int sampleCount() {
        return samples.size();
    }

    /** Returns the p50 (median) latency in ms, or 0 if no samples. */
    public long p50() { return percentile(50); }

    /** Returns the p90 latency in ms, or 0 if no samples. */
    public long p90() { return percentile(90); }

    /** Returns the p95 latency in ms, or 0 if no samples. */
    public long p95() { return percentile(95); }

    /** Returns the p99 latency in ms, or 0 if no samples. */
    public long p99() { return percentile(99); }

    /** Returns the minimum recorded latency, or 0 if no samples. */
    public long min() {
        if (samples.isEmpty()) return 0;
        ensureSorted();
        return samples.get(0);
    }

    /** Returns the maximum recorded latency, or 0 if no samples. */
    public long max() {
        if (samples.isEmpty()) return 0;
        ensureSorted();
        return samples.get(samples.size() - 1);
    }

    /** Returns the arithmetic mean of all samples, or 0 if no samples. */
    public double mean() {
        if (samples.isEmpty()) return 0;
        return samples.stream().mapToLong(Long::longValue).average().orElse(0);
    }

    /**
     * Computes the given percentile (0–100) using the nearest-rank method.
     *
     * @param p percentile value in [0, 100]
     * @return the percentile value in ms, or 0 if no samples
     */
    public long percentile(int p) {
        if (samples.isEmpty()) return 0;
        if (p < 0 || p > 100) throw new IllegalArgumentException("Percentile must be 0–100: " + p);
        ensureSorted();
        if (p == 0) return samples.get(0);
        int idx = (int) Math.ceil(p / 100.0 * samples.size()) - 1;
        return samples.get(Math.min(idx, samples.size() - 1));
    }

    /** Removes all recorded samples. */
    public void clear() {
        samples.clear();
        sorted = true;
    }

    // -------------------------------------------------------------------------

    private void ensureSorted() {
        if (!sorted) {
            Collections.sort(samples);
            sorted = true;
        }
    }
}
