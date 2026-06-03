package io.gauss.vigil.abtest;

import java.util.List;
import java.util.Optional;

/**
 * Computes statistical significance between two model versions in an A/B test
 * (Vigil module, HU-056).
 *
 * <p>Two test methods are available:
 * <ul>
 *   <li><b>Proportion Z-test</b> ({@link #testProportions}) — for binary outcomes
 *       (success / failure).  The test uses a pooled proportion estimate and
 *       a standard normal approximation.</li>
 *   <li><b>Welch's t-test</b> ({@link #testMeans}) — for continuous metrics
 *       (latency, accuracy, error rate).  Uses the Welch-Satterthwaite equation
 *       for degrees of freedom and approximates the t CDF with a normal
 *       distribution, which is accurate for sample sizes &gt; 30.</li>
 * </ul>
 *
 * <p>Both methods return an {@link ABTestResult} with the p-value, per-version
 * means, sample sizes, and a recommended winner if the result is significant.
 *
 * <p>Usage:
 * <pre>{@code
 * StatisticalTestService svc = new StatisticalTestService();
 * List<ABTestSample> samples = ... ;
 *
 * ABTestResult result = svc.testProportions("v1", "v2", samples, 0.05);
 * if (result.significant()) {
 *     System.out.println("Winner: " + result.recommendedWinner());
 * }
 * }</pre>
 */
public final class StatisticalTestService {

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Proportion Z-test for binary outcomes (values are 0 or 1).
     *
     * @param versionA         label for version A
     * @param versionB         label for version B
     * @param samples          combined samples from both versions
     * @param significanceLevel alpha (e.g., {@code 0.05})
     * @return test result
     * @throws IllegalArgumentException if either version has fewer than 2 samples
     */
    public ABTestResult testProportions(String versionA, String versionB,
                                         List<ABTestSample> samples,
                                         double significanceLevel) {
        double[] statsA = stats(versionA, samples);
        double[] statsB = stats(versionB, samples);
        int nA = (int) statsA[2];
        int nB = (int) statsB[2];
        validateMinSamples(nA, versionA);
        validateMinSamples(nB, versionB);

        double pA = statsA[0];   // proportion A
        double pB = statsB[0];   // proportion B
        double pPool = (pA * nA + pB * nB) / (nA + nB);
        double se = Math.sqrt(pPool * (1.0 - pPool) * (1.0 / nA + 1.0 / nB));

        double z = (se == 0.0) ? 0.0 : (pA - pB) / se;
        double pValue = 2.0 * normalCdfComplement(Math.abs(z));

        return buildResult(versionA, versionB, pA, pB, nA, nB, pValue, significanceLevel);
    }

    /**
     * Welch's t-test for continuous metrics (means of arbitrary distributions).
     *
     * @param versionA         label for version A
     * @param versionB         label for version B
     * @param samples          combined samples from both versions
     * @param significanceLevel alpha (e.g., {@code 0.05})
     * @return test result
     */
    public ABTestResult testMeans(String versionA, String versionB,
                                   List<ABTestSample> samples,
                                   double significanceLevel) {
        List<ABTestSample> sa = filterVersion(versionA, samples);
        List<ABTestSample> sb = filterVersion(versionB, samples);
        validateMinSamples(sa.size(), versionA);
        validateMinSamples(sb.size(), versionB);

        double meanA = mean(sa);
        double meanB = mean(sb);
        double varA  = variance(sa, meanA);
        double varB  = variance(sb, meanB);
        int    nA    = sa.size();
        int    nB    = sb.size();

        double se = Math.sqrt(varA / nA + varB / nB);
        double t  = (se == 0.0) ? 0.0 : (meanA - meanB) / se;
        // Use normal approximation for large samples (>= 30)
        double pValue = 2.0 * normalCdfComplement(Math.abs(t));

        return buildResult(versionA, versionB, meanA, meanB, nA, nB, pValue, significanceLevel);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static ABTestResult buildResult(String vA, String vB,
                                             double meanA, double meanB,
                                             int nA, int nB,
                                             double pValue,
                                             double alpha) {
        boolean significant = pValue < alpha;
        Optional<String> winner = significant
                ? Optional.of(meanA >= meanB ? vA : vB)
                : Optional.empty();
        return new ABTestResult(vA, vB, meanA, meanB, nA, nB,
                                pValue, significant, alpha, winner);
    }

    private static double[] stats(String version, List<ABTestSample> samples) {
        List<ABTestSample> filtered = filterVersion(version, samples);
        int n = filtered.size();
        if (n == 0) return new double[]{0.0, 0.0, 0.0};
        double sum = filtered.stream().mapToDouble(ABTestSample::value).sum();
        double mean = sum / n;
        double var  = variance(filtered, mean);
        return new double[]{mean, var, n};
    }

    private static List<ABTestSample> filterVersion(String version, List<ABTestSample> s) {
        return s.stream().filter(x -> x.version().equals(version)).toList();
    }

    private static double mean(List<ABTestSample> samples) {
        return samples.stream().mapToDouble(ABTestSample::value).average().orElse(0.0);
    }

    private static double variance(List<ABTestSample> samples, double mean) {
        int n = samples.size();
        if (n < 2) return 0.0;
        double sumSq = samples.stream()
                .mapToDouble(s -> (s.value() - mean) * (s.value() - mean)).sum();
        return sumSq / (n - 1);
    }

    /**
     * Complementary CDF of the standard normal: P(Z > z).
     * Uses the Abramowitz &amp; Stegun rational approximation (max error 7.5e-8).
     */
    static double normalCdfComplement(double z) {
        if (z < 0) return 1.0 - normalCdfComplement(-z);
        double t  = 1.0 / (1.0 + 0.2316419 * z);
        double poly = t * (0.319381530
                    + t * (-0.356563782
                    + t * (1.781477937
                    + t * (-1.821255978
                    + t *  1.330274429))));
        double pdf = Math.exp(-0.5 * z * z) / Math.sqrt(2.0 * Math.PI);
        return pdf * poly;
    }

    private static void validateMinSamples(int n, String version) {
        if (n < 2) throw new IllegalArgumentException(
                "Need at least 2 samples for version '" + version + "', got " + n);
    }
}
