package io.gauss.vigil.abtest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StatisticalTestService}.
 * Covers HU-056 acceptance criteria.
 */
class StatisticalTestServiceTest {

    private StatisticalTestService svc;

    @BeforeEach
    void setUp() {
        svc = new StatisticalTestService();
    }

    // -------------------------------------------------------------------------
    // ABTestSample helpers
    // -------------------------------------------------------------------------

    @Test
    void abTestSample_success_returnsOne() {
        assertThat(ABTestSample.success("A").value()).isEqualTo(1.0);
    }

    @Test
    void abTestSample_failure_returnsZero() {
        assertThat(ABTestSample.failure("B").value()).isEqualTo(0.0);
    }

    // -------------------------------------------------------------------------
    // Proportion Z-test — obvious winner
    // -------------------------------------------------------------------------

    @Test
    void proportionTest_clearDifference_isSignificant() {
        // A: 90 successes / 100 total (90 %)
        // B: 50 successes / 100 total (50 %)  → obvious difference
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 90, 10);
        addBinary(samples, "B", 50, 50);

        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);

        assertThat(result.significant()).isTrue();
        assertThat(result.pValue()).isLessThan(0.05);
    }

    @Test
    void proportionTest_clearWinner_isVersionA() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 90, 10);
        addBinary(samples, "B", 50, 50);

        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);

        assertThat(result.recommendedWinner()).hasValue("A");
    }

    @Test
    void proportionTest_noDifference_isNotSignificant() {
        // A: 70/100, B: 71/100 — virtually identical
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 70, 30);
        addBinary(samples, "B", 71, 29);

        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);

        assertThat(result.significant()).isFalse();
        assertThat(result.recommendedWinner()).isEmpty();
    }

    @Test
    void proportionTest_pValue_inBoundsZeroToOne() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 60, 40);
        addBinary(samples, "B", 55, 45);

        double pValue = svc.testProportions("A", "B", samples, 0.05).pValue();

        assertThat(pValue).isBetween(0.0, 1.0);
    }

    // -------------------------------------------------------------------------
    // Proportion Z-test — metadata
    // -------------------------------------------------------------------------

    @Test
    void proportionTest_sampleSizes_correct() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 80, 20);   // 100 total
        addBinary(samples, "B", 60, 15);   // 75 total

        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);

        assertThat(result.sampleSizeA()).isEqualTo(100);
        assertThat(result.sampleSizeB()).isEqualTo(75);
    }

    @Test
    void proportionTest_means_correct() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 80, 20);   // mean = 0.80
        addBinary(samples, "B", 60, 40);   // mean = 0.60

        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);

        assertThat(result.meanA()).isCloseTo(0.80, within(0.001));
        assertThat(result.meanB()).isCloseTo(0.60, within(0.001));
    }

    @Test
    void proportionTest_significanceLevel_stored() {
        List<ABTestSample> samples = buildEqualSamples("A", "B", 50);
        ABTestResult result = svc.testProportions("A", "B", samples, 0.01);
        assertThat(result.significanceLevel()).isEqualTo(0.01);
    }

    // -------------------------------------------------------------------------
    // Welch's t-test — continuous metrics
    // -------------------------------------------------------------------------

    @Test
    void tTest_clearDifference_isSignificant() {
        // A: latency around 100ms, B: around 200ms → should be significant
        List<ABTestSample> samples = new ArrayList<>();
        for (int i = 0; i < 100; i++) samples.add(new ABTestSample("A", 100 + (i % 5)));
        for (int i = 0; i < 100; i++) samples.add(new ABTestSample("B", 200 + (i % 5)));

        ABTestResult result = svc.testMeans("A", "B", samples, 0.05);

        assertThat(result.significant()).isTrue();
        assertThat(result.recommendedWinner()).isPresent();
    }

    @Test
    void tTest_noDifference_notSignificant() {
        List<ABTestSample> samples = new ArrayList<>();
        // Identical distributions
        for (int i = 0; i < 50; i++) samples.add(new ABTestSample("A", 50.0 + i * 0.1));
        for (int i = 0; i < 50; i++) samples.add(new ABTestSample("B", 50.0 + i * 0.1));

        ABTestResult result = svc.testMeans("A", "B", samples, 0.05);

        assertThat(result.pValue()).isGreaterThan(0.05);
    }

    // -------------------------------------------------------------------------
    // Confidence intervals
    // -------------------------------------------------------------------------

    @Test
    void ci95_lowerBound_lessThanMean() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 70, 30);
        addBinary(samples, "B", 50, 50);
        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);
        assertThat(result.ci95LowerA()).isLessThan(result.meanA());
        assertThat(result.ci95LowerB()).isLessThan(result.meanB());
    }

    @Test
    void ci95_upperBound_greaterThanMean() {
        List<ABTestSample> samples = new ArrayList<>();
        addBinary(samples, "A", 70, 30);
        addBinary(samples, "B", 50, 50);
        ABTestResult result = svc.testProportions("A", "B", samples, 0.05);
        assertThat(result.ci95UpperA()).isGreaterThan(result.meanA());
        assertThat(result.ci95UpperB()).isGreaterThan(result.meanB());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void proportionTest_tooFewSamples_throws() {
        List<ABTestSample> samples = List.of(ABTestSample.success("A"));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> svc.testProportions("A", "B", samples, 0.05));
    }

    @Test
    void normalCdfComplement_atZero_isHalf() {
        assertThat(StatisticalTestService.normalCdfComplement(0.0))
                .isCloseTo(0.5, within(0.001));
    }

    @Test
    void normalCdfComplement_at196_isApprox025() {
        // P(Z > 1.96) ≈ 0.025 for standard normal
        assertThat(StatisticalTestService.normalCdfComplement(1.96))
                .isCloseTo(0.025, within(0.002));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void addBinary(List<ABTestSample> list,
                                   String version,
                                   int successes, int failures) {
        for (int i = 0; i < successes; i++) list.add(ABTestSample.success(version));
        for (int i = 0; i < failures;  i++) list.add(ABTestSample.failure(version));
    }

    private static List<ABTestSample> buildEqualSamples(String vA, String vB, int each) {
        List<ABTestSample> s = new ArrayList<>();
        addBinary(s, vA, each / 2, each / 2);
        addBinary(s, vB, each / 2, each / 2);
        return s;
    }
}
