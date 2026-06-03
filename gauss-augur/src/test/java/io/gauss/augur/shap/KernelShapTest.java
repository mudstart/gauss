package io.gauss.augur.shap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link KernelShap}, {@link ShapValue} and {@link ExplanationResult}.
 * Covers HU-059 acceptance criteria.
 *
 * <p>Key property: for a linear model {@code f(x) = Σ c_i * x_i}, the SHAP
 * value for feature {@code i} equals {@code c_i * (x_i - background_i)}.
 */
class KernelShapTest {

    // Background = zeros; predictors are simple linear functions
    private static final double[] BACKGROUND_ZEROS = new double[]{0.0, 0.0, 0.0};
    private static final String[] FEATURE_NAMES    = {"age", "income", "tenure"};

    // f(x) = 2*x0 + 3*x1 + 1*x2
    private static final double[] COEFFICIENTS = {2.0, 3.0, 1.0};

    private KernelShap shap;

    @BeforeEach
    void setUp() {
        shap = new KernelShap(BACKGROUND_ZEROS, 2048, new Random(42));
    }

    // -------------------------------------------------------------------------
    // ShapValue record
    // -------------------------------------------------------------------------

    @Test
    void shapValue_positiveImpact_isPositive() {
        assertThat(new ShapValue("x", 0.5, 1).isPositive()).isTrue();
    }

    @Test
    void shapValue_negativeImpact_isNotPositive() {
        assertThat(new ShapValue("x", -0.3, 2).isPositive()).isFalse();
    }

    @Test
    void shapValue_toString_containsFeatureName() {
        assertThat(new ShapValue("income", 1.23, 1).toString()).contains("income");
    }

    // -------------------------------------------------------------------------
    // ExplanationResult
    // -------------------------------------------------------------------------

    @Test
    void explanationResult_impactOf_returnsCorrectValue() {
        ExplanationResult result = shap.explain(
                new double[]{1.0, 1.0, 1.0}, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);
        assertThat(result.impactOf("income")).isCloseTo(3.0, within(0.2));
    }

    @Test
    void explanationResult_impactOf_unknownFeature_returnsZero() {
        ExplanationResult result = shap.explain(
                new double[]{1.0, 1.0, 1.0}, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);
        assertThat(result.impactOf("nonexistent")).isZero();
    }

    @Test
    void explanationResult_predictionValue_correct() {
        double[] input = {1.0, 2.0, 3.0};
        ExplanationResult result = shap.explain(input, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);
        // f([1,2,3]) = 2*1 + 3*2 + 1*3 = 11
        assertThat(result.predictionValue()).isCloseTo(11.0, within(1e-9));
    }

    @Test
    void explanationResult_baselineValue_zeroForZeroBackground() {
        double[] input = {1.0, 1.0, 1.0};
        ExplanationResult result = shap.explain(input, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);
        assertThat(result.baselineValue()).isCloseTo(0.0, within(1e-9));
    }

    // -------------------------------------------------------------------------
    // Linear model SHAP correctness
    // -------------------------------------------------------------------------

    @Test
    void linearModel_shapValues_equalCoefficientTimesInput() {
        // For f(x) = Σ c_i * x_i with background = 0:
        //   SHAP(x_i) = c_i * (x_i - 0) = c_i * x_i
        double[] input = {1.0, 1.0, 1.0};
        ExplanationResult result = shap.explain(input, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);

        assertThat(result.impactOf("age"))    .isCloseTo(2.0, within(0.15));
        assertThat(result.impactOf("income")) .isCloseTo(3.0, within(0.15));
        assertThat(result.impactOf("tenure")) .isCloseTo(1.0, within(0.15));
    }

    @Test
    void linearModel_sumOfShapEqualsPredicitionMinusBaseline() {
        double[] input = {2.0, 1.0, 3.0};
        ExplanationResult result = shap.explain(input, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);

        double expected = result.predictionValue() - result.baselineValue();
        assertThat(result.totalAttributed()).isCloseTo(expected, within(0.3));
    }

    @Test
    void constantModel_allShapValuesNearZero() {
        ExplanationResult result = shap.explain(
                new double[]{5.0, 3.0, 2.0}, FEATURE_NAMES,
                x -> 7.0,   // constant predictor
                3);
        result.shapValues().forEach(sv ->
                assertThat(sv.impact()).isCloseTo(0.0, within(0.01)));
    }

    // -------------------------------------------------------------------------
    // Ranking
    // -------------------------------------------------------------------------

    @Test
    void topFeatures_limitsReturnedValues() {
        ExplanationResult result = shap.explain(
                new double[]{1.0, 1.0, 1.0}, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 2);
        assertThat(result.shapValues()).hasSize(2);
    }

    @Test
    void topFeature_rank1_isHighestAbsoluteImpact() {
        double[] input = {1.0, 1.0, 1.0};
        ExplanationResult result = shap.explain(input, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);

        // income has coefficient 3 → highest impact
        assertThat(result.shapValues().get(0).featureName()).isEqualTo("income");
        assertThat(result.shapValues().get(0).rank()).isEqualTo(1);
    }

    @Test
    void ranks_areSequential() {
        ExplanationResult result = shap.explain(
                new double[]{1.0, 1.0, 1.0}, FEATURE_NAMES,
                x -> linearModel(x, COEFFICIENTS), 3);
        for (int i = 0; i < result.shapValues().size(); i++) {
            assertThat(result.shapValues().get(i).rank()).isEqualTo(i + 1);
        }
    }

    // -------------------------------------------------------------------------
    // Kernel weight
    // -------------------------------------------------------------------------

    @Test
    void kernelWeight_allAbsent_isLarge() {
        double w = KernelShap.kernelWeight(3, new int[]{0, 0, 0});
        assertThat(w).isGreaterThan(1000);
    }

    @Test
    void kernelWeight_allPresent_isLarge() {
        double w = KernelShap.kernelWeight(3, new int[]{1, 1, 1});
        assertThat(w).isGreaterThan(1000);
    }

    @Test
    void kernelWeight_singleFeature_isPositive() {
        double w = KernelShap.kernelWeight(3, new int[]{1, 0, 0});
        assertThat(w).isPositive();
    }

    // -------------------------------------------------------------------------
    // Gaussian elimination
    // -------------------------------------------------------------------------

    @Test
    void gaussianElimination_solves2x2System() {
        // 2x + y = 5 → x=2, y=1
        double[][] A = {{2, 1}, {1, -1}};
        double[]   b = {5, 1};
        double[]   x = KernelShap.gaussianElimination(A, b);
        assertThat(x[0]).isCloseTo(2.0, within(1e-9));
        assertThat(x[1]).isCloseTo(1.0, within(1e-9));
    }

    @Test
    void gaussianElimination_identity_returnsSolution() {
        double[][] A = {{1, 0}, {0, 1}};
        double[]   b = {3, 7};
        double[]   x = KernelShap.gaussianElimination(A, b);
        assertThat(x[0]).isCloseTo(3.0, within(1e-9));
        assertThat(x[1]).isCloseTo(7.0, within(1e-9));
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void mismatchedLengths_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> shap.explain(
                        new double[]{1.0, 2.0},          // length 2
                        FEATURE_NAMES,                    // length 3
                        x -> 0.0, 3));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private static double linearModel(double[] x, double[] coeff) {
        double sum = 0;
        for (int i = 0; i < x.length; i++) sum += coeff[i] * x[i];
        return sum;
    }
}
