package io.gauss.augur.drift;

import java.time.Instant;

/**
 * Immutable result of a single drift evaluation (HU-037).
 *
 * @param endpointName   the endpoint whose inputs were analysed
 * @param metric         drift metric used (e.g., {@code "PSI"})
 * @param score          computed drift score
 * @param threshold      the configured alert threshold
 * @param alert          {@code true} when {@code score > threshold}
 * @param sampleSize     number of current observations used
 * @param evaluatedAt    timestamp of the evaluation
 */
public record DriftReport(
        String  endpointName,
        String  metric,
        double  score,
        double  threshold,
        boolean alert,
        int     sampleSize,
        Instant evaluatedAt
) {

    /** Returns a human-readable summary for logging. */
    public String summary() {
        return String.format(
                "[%s] Drift %s — metric=%s score=%.4f threshold=%.4f samples=%d",
                endpointName, alert ? "ALERT" : "OK",
                metric, score, threshold, sampleSize);
    }
}
