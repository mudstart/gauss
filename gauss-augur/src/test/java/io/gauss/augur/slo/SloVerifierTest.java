package io.gauss.augur.slo;

import io.gauss.core.annotation.LatencySLO;
import io.gauss.core.annotation.MLEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link SloVerifier}, {@link LatencyStats} and
 * {@link SloVerificationResult}.
 * Covers HU-042 acceptance criteria.
 */
class SloVerifierTest {

    // -------------------------------------------------------------------------
    // Fixture endpoint class
    // -------------------------------------------------------------------------

    @LatencySLO(p50 = "5ms", p95 = "20ms", p99 = "50ms")
    static class ChurnEndpoint { }

    @LatencySLO(p99 = "10ms")
    static class StrictEndpoint { }

    static class NoSloEndpoint { }

    // -------------------------------------------------------------------------

    private SloVerifier verifier;

    @BeforeEach
    void setUp() {
        verifier = new SloVerifier();
    }

    // -------------------------------------------------------------------------
    // LatencyStats — recording
    // -------------------------------------------------------------------------

    @Test
    void stats_sampleCount_incrementsOnRecord() {
        LatencyStats stats = new LatencyStats();
        stats.record(10);
        stats.record(20);
        assertThat(stats.sampleCount()).isEqualTo(2);
    }

    @Test
    void stats_negativeLatency_throws() {
        LatencyStats stats = new LatencyStats();
        assertThatIllegalArgumentException()
                .isThrownBy(() -> stats.record(-1));
    }

    @Test
    void stats_zeroLatency_isValid() {
        LatencyStats stats = new LatencyStats();
        assertThatNoException().isThrownBy(() -> stats.record(0));
    }

    // -------------------------------------------------------------------------
    // LatencyStats — percentiles
    // -------------------------------------------------------------------------

    @Test
    void stats_p50_correctForOddCount() {
        LatencyStats stats = new LatencyStats();
        LongStream.rangeClosed(1, 101).forEach(stats::record);  // 1..101
        assertThat(stats.p50()).isEqualTo(51);
    }

    @Test
    void stats_p99_largeDataset() {
        LatencyStats stats = new LatencyStats();
        LongStream.rangeClosed(1, 100).forEach(stats::record);
        // p99 = nearest rank = ceil(99/100 * 100) - 1 = 98th index = 99
        assertThat(stats.p99()).isEqualTo(99);
    }

    @Test
    void stats_min_returnsSmallest() {
        LatencyStats stats = new LatencyStats();
        stats.record(50); stats.record(5); stats.record(30);
        assertThat(stats.min()).isEqualTo(5);
    }

    @Test
    void stats_max_returnsLargest() {
        LatencyStats stats = new LatencyStats();
        stats.record(50); stats.record(5); stats.record(30);
        assertThat(stats.max()).isEqualTo(50);
    }

    @Test
    void stats_empty_returnsZeroForAllPercentiles() {
        LatencyStats stats = new LatencyStats();
        assertThat(stats.p50()).isZero();
        assertThat(stats.p95()).isZero();
        assertThat(stats.p99()).isZero();
    }

    @Test
    void stats_mean_isCorrect() {
        LatencyStats stats = new LatencyStats();
        stats.record(10); stats.record(20); stats.record(30);
        assertThat(stats.mean()).isCloseTo(20.0, within(0.001));
    }

    @Test
    void stats_clear_removesAllSamples() {
        LatencyStats stats = new LatencyStats();
        stats.record(10); stats.record(20);
        stats.clear();
        assertThat(stats.sampleCount()).isZero();
    }

    // -------------------------------------------------------------------------
    // SloVerifier.parseMs
    // -------------------------------------------------------------------------

    @Test
    void parseMs_milliseconds() {
        assertThat(SloVerifier.parseMs("50ms")).isEqualTo(50);
    }

    @Test
    void parseMs_seconds() {
        assertThat(SloVerifier.parseMs("2s")).isEqualTo(2000);
    }

    @Test
    void parseMs_minutes() {
        assertThat(SloVerifier.parseMs("1m")).isEqualTo(60_000);
    }

    @Test
    void parseMs_invalidFormat_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SloVerifier.parseMs("50x"));
    }

    // -------------------------------------------------------------------------
    // SloVerifier.verify — PASS
    // -------------------------------------------------------------------------

    @Test
    void verify_allTargetsMet_passes() {
        LatencyStats stats = fastStats();
        SloVerificationResult result = verifier.verify(ChurnEndpoint.class, stats);
        assertThat(result.passed()).isTrue();
    }

    @Test
    void verify_allTargetsMet_noViolations() {
        SloVerificationResult result = verifier.verify(ChurnEndpoint.class, fastStats());
        assertThat(result.violations()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // SloVerifier.verify — FAIL
    // -------------------------------------------------------------------------

    @Test
    void verify_p99Exceeded_fails() {
        LatencyStats stats = new LatencyStats();
        LongStream.range(0, 100).map(i -> 100L).forEach(stats::record); // all 100ms
        SloVerificationResult result = verifier.verify(StrictEndpoint.class, stats);
        assertThat(result.passed()).isFalse();
    }

    @Test
    void verify_p99Exceeded_violationMessage_containsInfo() {
        LatencyStats stats = new LatencyStats();
        LongStream.range(0, 100).map(i -> 100L).forEach(stats::record);
        SloVerificationResult result = verifier.verify(StrictEndpoint.class, stats);
        assertThat(result.violations().get(0))
                .contains("p99")
                .contains("target=10ms")
                .contains("actual=100ms");
    }

    @Test
    void verify_multipleViolations_allReported() {
        LatencyStats stats = new LatencyStats();
        LongStream.range(0, 100).map(i -> 200L).forEach(stats::record);
        SloVerificationResult result = verifier.verify(ChurnEndpoint.class, stats);
        // p50=200ms > 5ms, p95=200ms > 20ms, p99=200ms > 50ms → 3 violations
        assertThat(result.violations()).hasSize(3);
    }

    @Test
    void verify_noAnnotation_throws() {
        LatencyStats stats = new LatencyStats();
        stats.record(10);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> verifier.verify(NoSloEndpoint.class, stats));
    }

    // -------------------------------------------------------------------------
    // SloVerificationResult
    // -------------------------------------------------------------------------

    @Test
    void result_measuredPercentiles_stored() {
        LatencyStats stats = fastStats();
        SloVerificationResult r = verifier.verify(ChurnEndpoint.class, stats);
        assertThat(r.measuredP50()).isGreaterThanOrEqualTo(0);
        assertThat(r.measuredP95()).isGreaterThanOrEqualTo(0);
        assertThat(r.measuredP99()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void result_sampleCount_matches() {
        LatencyStats stats = new LatencyStats();
        for (int i = 0; i < 50; i++) stats.record(1);
        SloVerificationResult r = verifier.verify(ChurnEndpoint.class, stats);
        assertThat(r.sampleCount()).isEqualTo(50);
    }

    @Test
    void result_summary_containsEndpointName() {
        SloVerificationResult r = verifier.verify(ChurnEndpoint.class, fastStats());
        assertThat(r.summary()).contains("ChurnEndpoint");
    }

    @Test
    void result_summary_pass_containsPass() {
        SloVerificationResult r = verifier.verify(ChurnEndpoint.class, fastStats());
        assertThat(r.summary()).contains("PASS");
    }

    @Test
    void result_summary_fail_containsFail() {
        LatencyStats slow = new LatencyStats();
        LongStream.range(0, 100).map(i -> 200L).forEach(slow::record);
        SloVerificationResult r = verifier.verify(ChurnEndpoint.class, slow);
        assertThat(r.summary()).contains("FAIL");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /** Stats where all samples are 1ms (well within the ChurnEndpoint SLOs). */
    private static LatencyStats fastStats() {
        LatencyStats s = new LatencyStats();
        LongStream.range(0, 100).map(i -> 1L).forEach(s::record);
        return s;
    }
}
