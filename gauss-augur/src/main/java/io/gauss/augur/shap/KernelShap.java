package io.gauss.augur.shap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * Kernel SHAP approximation for arbitrary black-box predictors (HU-059).
 *
 * <p>Kernel SHAP computes Shapley values using a weighted linear regression on
 * feature-coalition samples.  For {@code n ≤ 16} features all {@code 2^n}
 * coalitions are enumerated exactly; for larger models a configurable sample
 * of random coalitions is used instead.
 *
 * <p>Key property verified by unit tests: for a linear model
 * {@code f(x) = Σ c_i * x_i}, the SHAP value for feature {@code i} equals
 * {@code c_i * (x_i - background_i)}.
 *
 * <p>Usage:
 * <pre>{@code
 * KernelShap shap = new KernelShap(new double[]{0.0, 0.0, 0.0});
 *
 * ExplanationResult result = shap.explain(
 *         new double[]{1.0, 2.0, 3.0},
 *         new String[]{"age", "income", "tenure"},
 *         input -> model.predict(input),
 *         5);
 *
 * result.shapValues().forEach(v -> System.out.println(v));
 * }</pre>
 */
public final class KernelShap {

    /** Enumerate all 2^n coalitions when n is this small. */
    private static final int MAX_ENUMERATE = 16;

    private final double[]  background;
    private final int       numSamples;
    private final Random    random;

    // -------------------------------------------------------------------------

    /**
     * Creates an explainer with the given background data and default
     * 2048-sample limit for large models.
     *
     * @param background expected feature values (used when a feature is absent
     *                   from a coalition)
     */
    public KernelShap(double[] background) {
        this(background, 2048, new Random(42));
    }

    /**
     * Creates an explainer with explicit sample count and seeded random.
     * Use a fixed seed for reproducible unit tests.
     */
    public KernelShap(double[] background, int numSamples, Random random) {
        this.background = Arrays.copyOf(background, background.length);
        this.numSamples = numSamples;
        this.random     = random;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Explains the prediction for {@code input} using Kernel SHAP.
     *
     * @param input        the input vector to explain
     * @param featureNames names of the features (same length as {@code input})
     * @param predictor    the model function: {@code double[] → double}
     * @param topFeatures  number of top SHAP values to return (all if &le; 0)
     * @return explanation with SHAP values ordered by {@code |impact|} desc
     */
    public ExplanationResult explain(double[] input,
                                      String[] featureNames,
                                      Function<double[], Double> predictor,
                                      int topFeatures) {
        int n = input.length;
        if (n != background.length) throw new IllegalArgumentException(
                "input.length (" + n + ") must equal background.length (" + background.length + ")");

        double baseline    = predictor.apply(background);
        double prediction  = predictor.apply(input);

        // Build coalitions
        List<int[]> coalitions = n <= MAX_ENUMERATE
                ? enumerateAll(n)
                : sampleCoalitions(n);

        int m = coalitions.size();
        double[] responses = new double[m];
        double[] weights   = new double[m];

        for (int s = 0; s < m; s++) {
            int[]    z       = coalitions.get(s);
            double[] masked  = applyMask(input, background, z);
            responses[s] = predictor.apply(masked) - baseline;
            weights[s]   = kernelWeight(n, z);
        }

        // Solve weighted least squares: (Z^T W Z) φ = Z^T W y
        double[] phi = solveWLS(coalitions, responses, weights, n);

        // Build ranked ShapValue list
        List<ShapValue> shapValues = buildRankedValues(phi, featureNames, topFeatures);

        return new ExplanationResult(prediction, baseline, shapValues);
    }

    // -------------------------------------------------------------------------
    // Coalition generation
    // -------------------------------------------------------------------------

    private static List<int[]> enumerateAll(int n) {
        List<int[]> list = new ArrayList<>(1 << n);
        for (int mask = 0; mask < (1 << n); mask++) {
            int[] z = new int[n];
            for (int i = 0; i < n; i++) z[i] = (mask >> i) & 1;
            list.add(z);
        }
        return list;
    }

    private List<int[]> sampleCoalitions(int n) {
        List<int[]> list = new ArrayList<>(numSamples + 2);
        // Always include all-absent and all-present
        list.add(new int[n]);
        int[] ones = new int[n];
        Arrays.fill(ones, 1);
        list.add(ones);
        for (int s = 2; s < numSamples; s++) {
            int[] z = new int[n];
            for (int i = 0; i < n; i++) z[i] = random.nextInt(2);
            list.add(z);
        }
        return list;
    }

    // -------------------------------------------------------------------------
    // Kernel weight
    // -------------------------------------------------------------------------

    /**
     * Shapley kernel weight for a coalition of size {@code k} among {@code n}
     * features:
     * <pre>w = (n - 1) / (C(n, k) * k * (n - k))</pre>
     * Edge cases (k=0 and k=n) are assigned a large weight.
     */
    static double kernelWeight(int n, int[] z) {
        int k = 0;
        for (int v : z) k += v;
        if (k == 0 || k == n) return 1_000_000.0;
        long binom = binomialCoeff(n, k);
        return (n - 1.0) / (binom * k * (double)(n - k));
    }

    private static long binomialCoeff(int n, int k) {
        if (k > n - k) k = n - k;
        long result = 1;
        for (int i = 0; i < k; i++) {
            result = result * (n - i) / (i + 1);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Weighted least squares
    // -------------------------------------------------------------------------

    private static double[] solveWLS(List<int[]> coalitions,
                                      double[] responses,
                                      double[] weights,
                                      int n) {
        // Build Z^T W Z (n×n) and Z^T W y (n-vector)
        double[][] ZtWZ = new double[n][n];
        double[]   ZtWy = new double[n];

        for (int s = 0; s < coalitions.size(); s++) {
            int[]  z = coalitions.get(s);
            double w = weights[s];
            double y = responses[s];
            for (int i = 0; i < n; i++) {
                ZtWy[i] += w * z[i] * y;
                for (int j = 0; j < n; j++) {
                    ZtWZ[i][j] += w * z[i] * z[j];
                }
            }
        }
        // Small Tikhonov regularisation for stability
        for (int i = 0; i < n; i++) ZtWZ[i][i] += 1e-9;

        return gaussianElimination(ZtWZ, ZtWy);
    }

    /** Gaussian elimination with partial pivoting. Solves A·x = b in-place. */
    static double[] gaussianElimination(double[][] A, double[] b) {
        int n = b.length;
        double[][] M = new double[n][n + 1];
        for (int i = 0; i < n; i++) {
            System.arraycopy(A[i], 0, M[i], 0, n);
            M[i][n] = b[i];
        }
        for (int k = 0; k < n; k++) {
            int maxRow = k;
            for (int i = k + 1; i < n; i++) {
                if (Math.abs(M[i][k]) > Math.abs(M[maxRow][k])) maxRow = i;
            }
            double[] tmp = M[k]; M[k] = M[maxRow]; M[maxRow] = tmp;
            if (Math.abs(M[k][k]) < 1e-14) continue;
            for (int i = k + 1; i < n; i++) {
                double f = M[i][k] / M[k][k];
                for (int j = k; j <= n; j++) M[i][j] -= f * M[k][j];
            }
        }
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = M[i][n];
            for (int j = i + 1; j < n; j++) x[i] -= M[i][j] * x[j];
            if (Math.abs(M[i][i]) > 1e-14) x[i] /= M[i][i];
        }
        return x;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static double[] applyMask(double[] input, double[] background, int[] z) {
        double[] masked = new double[input.length];
        for (int i = 0; i < input.length; i++) {
            masked[i] = z[i] == 1 ? input[i] : background[i];
        }
        return masked;
    }

    private static List<ShapValue> buildRankedValues(double[] phi,
                                                       String[] featureNames,
                                                       int topFeatures) {
        record NamedPhi(String name, double value) {}
        List<NamedPhi> pairs = new ArrayList<>(phi.length);
        for (int i = 0; i < phi.length; i++) pairs.add(new NamedPhi(featureNames[i], phi[i]));

        pairs.sort(Comparator.comparingDouble(p -> -Math.abs(p.value())));

        int limit = (topFeatures <= 0 || topFeatures > pairs.size())
                ? pairs.size() : topFeatures;

        List<ShapValue> result = new ArrayList<>(limit);
        for (int rank = 1; rank <= limit; rank++) {
            NamedPhi p = pairs.get(rank - 1);
            result.add(new ShapValue(p.name(), p.value(), rank));
        }
        return result;
    }
}
