package io.gauss.augur.drift;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.DoubleStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DriftMonitorService} and {@link PSICalculator}.
 * Covers HU-037 acceptance criteria.
 */
class DriftMonitorServiceTest {

    private DriftMonitorService monitor;

    // Reference distribution: uniform 0–1
    private static final double[] REFERENCE = DoubleStream.iterate(0, d -> d + 0.01)
            .limit(100).toArray();

    @BeforeEach
    void setUp() {
        monitor = new DriftMonitorService();
        monitor.setReference("churn", REFERENCE, 10);
    }

    // -------------------------------------------------------------------------
    // PSICalculator
    // -------------------------------------------------------------------------

    @Test
    void psi_identicalDistributions_nearZero() {
        PSICalculator calc = new PSICalculator();
        double score = calc.compute(REFERENCE, REFERENCE, 10);
        assertThat(score).isCloseTo(0.0, within(0.01));
    }

    @Test
    void psi_completelyDifferentDistributions_highScore() {
        PSICalculator calc = new PSICalculator();
        // Current = all values at the high end (1–2) vs reference (0–1)
        double[] current = DoubleStream.iterate(1.0, d -> d + 0.01).limit(100).toArray();
        double score = calc.compute(REFERENCE, current, 10);
        assertThat(score).isGreaterThan(0.25);
    }

    @Test
    void psi_score_isNonNegative() {
        PSICalculator calc = new PSICalculator();
        double score = calc.compute(REFERENCE, REFERENCE, 10);
        assertThat(score).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void psi_emptyReference_returnsZero() {
        PSICalculator calc = new PSICalculator();
        assertThat(calc.compute(new double[0], REFERENCE, 10)).isZero();
    }

    @Test
    void psi_fromProportions_identical_nearZero() {
        PSICalculator calc = new PSICalculator();
        double[] props = {0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1, 0.1};
        assertThat(calc.computeFromProportions(props, props)).isCloseTo(0.0, within(0.001));
    }

    @Test
    void psi_fromProportions_differentLengths_throws() {
        PSICalculator calc = new PSICalculator();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> calc.computeFromProportions(
                        new double[]{0.5, 0.5}, new double[]{0.3, 0.3, 0.4}));
    }

    // -------------------------------------------------------------------------
    // DriftMonitorService — recording
    // -------------------------------------------------------------------------

    @Test
    void observationCount_zero_beforeAnyRecord() {
        assertThat(monitor.observationCount("churn")).isZero();
    }

    @Test
    void observationCount_incrementsOnRecord() {
        monitor.recordObservation("churn", 0.5);
        monitor.recordObservation("churn", 0.3);
        assertThat(monitor.observationCount("churn")).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // DriftMonitorService — evaluate
    // -------------------------------------------------------------------------

    @Test
    void evaluate_noObservations_returnsEmpty() {
        assertThat(monitor.evaluate("churn", 0.1, 10)).isEmpty();
    }

    @Test
    void evaluate_noReference_returnsEmpty() {
        DriftMonitorService noRef = new DriftMonitorService();
        noRef.recordObservation("no-ref", 0.5);
        assertThat(noRef.evaluate("no-ref", 0.1, 10)).isEmpty();
    }

    @Test
    void evaluate_stableDistribution_noAlert() {
        // Feed same distribution as reference → PSI ≈ 0
        for (int i = 0; i < 100; i++) monitor.recordObservation("churn", i * 0.01);
        Optional<DriftReport> report = monitor.evaluate("churn", 0.1, 10);
        assertThat(report).isPresent();
        assertThat(report.get().alert()).isFalse();
    }

    @Test
    void evaluate_shiftedDistribution_triggersAlert() {
        // Feed values mostly above 0.8 (shifted right vs reference 0–1)
        for (int i = 0; i < 100; i++) monitor.recordObservation("churn", 0.8 + i * 0.002);
        Optional<DriftReport> report = monitor.evaluate("churn", 0.1, 10);
        assertThat(report).isPresent();
        assertThat(report.get().alert()).isTrue();
    }

    @Test
    void evaluate_report_containsEndpointName() {
        monitor.recordObservation("churn", 0.5);
        DriftReport report = monitor.evaluate("churn", 0.1, 10).orElseThrow();
        assertThat(report.endpointName()).isEqualTo("churn");
    }

    @Test
    void evaluate_report_metricIsPsi() {
        monitor.recordObservation("churn", 0.5);
        DriftReport report = monitor.evaluate("churn", 0.1, 10).orElseThrow();
        assertThat(report.metric()).isEqualTo("PSI");
    }

    @Test
    void evaluate_report_sampleSizeCorrect() {
        for (int i = 0; i < 42; i++) monitor.recordObservation("churn", 0.5);
        DriftReport report = monitor.evaluate("churn", 0.1, 10).orElseThrow();
        assertThat(report.sampleSize()).isEqualTo(42);
    }

    @Test
    void evaluate_report_thresholdStored() {
        monitor.recordObservation("churn", 0.5);
        DriftReport report = monitor.evaluate("churn", 0.15, 10).orElseThrow();
        assertThat(report.threshold()).isEqualTo(0.15);
    }

    // -------------------------------------------------------------------------
    // DriftMonitorService — history
    // -------------------------------------------------------------------------

    @Test
    void history_recordsEachEvaluation() {
        monitor.recordObservation("churn", 0.5);
        monitor.evaluate("churn", 0.1, 10);
        monitor.recordObservation("churn", 0.6);
        monitor.evaluate("churn", 0.1, 10);
        assertThat(monitor.history()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // DriftReport
    // -------------------------------------------------------------------------

    @Test
    void report_summary_containsEndpointAndScore() {
        DriftReport r = new DriftReport("ep", "PSI", 0.35, 0.1, true, 100,
                java.time.Instant.now());
        assertThat(r.summary()).contains("ep").contains("PSI").contains("ALERT");
    }

    @Test
    void report_summary_ok_whenNoAlert() {
        DriftReport r = new DriftReport("ep", "PSI", 0.05, 0.1, false, 100,
                java.time.Instant.now());
        assertThat(r.summary()).contains("OK");
    }

    // -------------------------------------------------------------------------
    // Reset
    // -------------------------------------------------------------------------

    @Test
    void reset_clearsObservations() {
        monitor.recordObservation("churn", 0.5);
        monitor.reset("churn");
        assertThat(monitor.observationCount("churn")).isZero();
    }

    @Test
    void resetAll_clearsEverything() {
        monitor.recordObservation("churn", 0.5);
        monitor.evaluate("churn", 0.1, 10);
        monitor.resetAll();
        assertThat(monitor.history()).isEmpty();
        assertThat(monitor.evaluate("churn", 0.1, 10)).isEmpty();
    }
}
