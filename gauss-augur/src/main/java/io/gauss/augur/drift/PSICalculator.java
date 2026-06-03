package io.gauss.augur.drift;

import java.util.Arrays;

/**
 * Computes the Population Stability Index (PSI) between a reference distribution
 * and a current distribution (Augur module, HU-037).
 *
 * <p>PSI is defined as:
 * <pre>
 *   PSI = &Sigma; (A_i - E_i) &times; ln(A_i / E_i)
 * </pre>
 * where {@code A_i} is the actual (current) proportion in bucket {@code i} and
 * {@code E_i} is the expected (reference) proportion.
 *
 * <p>Interpretation:
 * <ul>
 *   <li>{@code PSI < 0.10} — no significant change</li>
 *   <li>{@code 0.10 &le; PSI &le; 0.25} — moderate shift, investigate</li>
 *   <li>{@code PSI > 0.25} — major shift, model likely needs retraining</li>
 * </ul>
 *
 * <p>A small epsilon ({@code 1e-6}) prevents {@code ln(0)} when a bucket is empty.
 */
public final class PSICalculator {

    private static final double EPSILON = 1e-6;

    // -------------------------------------------------------------------------

    /**
     * Computes PSI from raw sample arrays by binning them into equal-width
     * buckets determined by the reference distribution's range.
     *
     * @param reference reference (training) samples
     * @param current   current (production) samples
     * @param numBuckets number of equal-width buckets (typically 10 or 20)
     * @return PSI score &ge; 0
     */
    public double compute(double[] reference, double[] current, int numBuckets) {
        if (reference.length == 0 || current.length == 0) return 0.0;
        if (numBuckets < 2) throw new IllegalArgumentException("numBuckets must be >= 2");

        double min = Arrays.stream(reference).min().orElse(0);
        double max = Arrays.stream(reference).max().orElse(1);
        if (min == max) { min -= 0.5; max += 0.5; }

        double[] refBuckets = bucket(reference, min, max, numBuckets);
        double[] curBuckets = bucket(current,   min, max, numBuckets);

        double psi = 0.0;
        for (int i = 0; i < numBuckets; i++) {
            double e = Math.max(refBuckets[i], EPSILON);
            double a = Math.max(curBuckets[i], EPSILON);
            psi += (a - e) * Math.log(a / e);
        }
        return psi;
    }

    /**
     * Computes PSI directly from pre-computed proportions arrays.
     * Each element must be in [0, 1] and the arrays must have the same length.
     *
     * @param referenceProportions expected proportions per bucket
     * @param currentProportions   actual proportions per bucket
     * @return PSI score
     */
    public double computeFromProportions(double[] referenceProportions,
                                          double[] currentProportions) {
        if (referenceProportions.length != currentProportions.length) {
            throw new IllegalArgumentException("Arrays must have equal length");
        }
        double psi = 0.0;
        for (int i = 0; i < referenceProportions.length; i++) {
            double e = Math.max(referenceProportions[i], EPSILON);
            double a = Math.max(currentProportions[i],   EPSILON);
            psi += (a - e) * Math.log(a / e);
        }
        return psi;
    }

    // -------------------------------------------------------------------------

    /** Bins samples into equal-width buckets and returns proportions. */
    private static double[] bucket(double[] samples, double min, double max, int n) {
        double width = (max - min) / n;
        int[] counts = new int[n];
        for (double v : samples) {
            int idx = (int) ((v - min) / width);
            idx = Math.min(Math.max(idx, 0), n - 1);
            counts[idx]++;
        }
        double[] proportions = new double[n];
        for (int i = 0; i < n; i++) {
            proportions[i] = (double) counts[i] / samples.length;
        }
        return proportions;
    }
}
