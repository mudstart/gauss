package io.gauss.augur.version;

/**
 * Pairs a model version identifier with its relative traffic weight (HU-019).
 *
 * @param version identifier string (e.g., {@code "v1"}, {@code "2024-12-01"})
 * @param weight  relative integer weight; must be &gt; 0
 */
public record VersionWeight(String version, int weight) {

    public VersionWeight {
        if (weight <= 0) throw new IllegalArgumentException(
                "Weight must be positive, got " + weight + " for version '" + version + "'");
    }
}
