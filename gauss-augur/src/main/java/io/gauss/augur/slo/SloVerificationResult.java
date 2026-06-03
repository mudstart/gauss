package io.gauss.augur.slo;

import java.util.List;

/**
 * Result of an SLO verification run (HU-042).
 *
 * @param endpointName  the endpoint that was benchmarked
 * @param passed        {@code true} if all configured SLO targets were met
 * @param violations    human-readable description of each violated target
 * @param measuredP50   measured p50 latency in ms
 * @param measuredP95   measured p95 latency in ms
 * @param measuredP99   measured p99 latency in ms
 * @param sampleCount   number of latency samples used in the evaluation
 */
public record SloVerificationResult(
        String       endpointName,
        boolean      passed,
        List<String> violations,
        long         measuredP50,
        long         measuredP95,
        long         measuredP99,
        int          sampleCount
) {

    public SloVerificationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
    }

    /** Returns a formatted summary suitable for logging. */
    public String summary() {
        String status = passed ? "PASS" : "FAIL";
        return String.format("[%s] SLO %s — p50=%dms p95=%dms p99=%dms samples=%d%s",
                endpointName, status, measuredP50, measuredP95, measuredP99, sampleCount,
                passed ? "" : "\n  " + String.join("\n  ", violations));
    }
}
