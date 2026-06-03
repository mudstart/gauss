package io.gauss.vigil.abtest;

import java.util.Optional;

/**
 * Result of a statistical significance analysis comparing two model versions
 * (HU-056).
 *
 * @param versionA          identifier for version A
 * @param versionB          identifier for version B
 * @param meanA             sample mean for version A
 * @param meanB             sample mean for version B
 * @param sampleSizeA       number of observations for version A
 * @param sampleSizeB       number of observations for version B
 * @param pValue            two-tailed p-value (lower = stronger evidence of difference)
 * @param significant       {@code true} when {@code pValue < significanceLevel}
 * @param significanceLevel the alpha level used for the test (e.g., {@code 0.05})
 * @param recommendedWinner the version with higher mean when {@code significant}, or empty
 */
public record ABTestResult(
        String           versionA,
        String           versionB,
        double           meanA,
        double           meanB,
        int              sampleSizeA,
        int              sampleSizeB,
        double           pValue,
        boolean          significant,
        double           significanceLevel,
        Optional<String> recommendedWinner
) {

    /** Lower 95 % confidence-interval bound for version A's mean. */
    public double ci95LowerA() {
        return ci95Margin(meanA, sampleSizeA, stdErr(meanA, sampleSizeA));
    }

    /** Upper 95 % confidence-interval bound for version A's mean. */
    public double ci95UpperA() {
        return meanA + 1.96 * stdErr(meanA, sampleSizeA);
    }

    /** Lower 95 % confidence-interval bound for version B's mean. */
    public double ci95LowerB() {
        return ci95Margin(meanB, sampleSizeB, stdErr(meanB, sampleSizeB));
    }

    /** Upper 95 % confidence-interval bound for version B's mean. */
    public double ci95UpperB() {
        return meanB + 1.96 * stdErr(meanB, sampleSizeB);
    }

    private static double ci95Margin(double mean, int n, double se) {
        return mean - 1.96 * se;
    }

    private static double stdErr(double proportion, int n) {
        if (n <= 0) return 0.0;
        return Math.sqrt(proportion * (1.0 - proportion) / n);
    }
}
