package io.gauss.flume.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link PipelineStatusService}. Covers HU-014 acceptance criteria.
 */
class PipelineStatusServiceTest {

    private PipelineStatusService svc;
    private final Instant T0 = Instant.parse("2026-05-30T08:00:00Z");
    private final Instant T1 = Instant.parse("2026-05-30T08:01:00Z");

    @BeforeEach
    void setUp() { svc = new PipelineStatusService(); }

    @Test
    void recordStart_returnsNonNullId() {
        assertThat(svc.recordStart("etl", T0)).isNotBlank();
    }

    @Test
    void recordStart_statusIsRunning() {
        String id = svc.recordStart("etl", T0);
        assertThat(svc.findById(id).orElseThrow().status())
                .isEqualTo(PipelineExecutionSummary.ExecutionStatus.RUNNING);
    }

    @Test
    void recordSuccess_updatesStatusToCompleted() {
        String id = svc.recordStart("etl", T0);
        svc.recordSuccess(id, T1);
        assertThat(svc.findById(id).orElseThrow().status())
                .isEqualTo(PipelineExecutionSummary.ExecutionStatus.COMPLETED);
    }

    @Test
    void recordSuccess_setsFinishedAt() {
        String id = svc.recordStart("etl", T0);
        svc.recordSuccess(id, T1);
        assertThat(svc.findById(id).orElseThrow().finishedAt()).hasValue(T1);
    }

    @Test
    void recordSuccess_durationIsCorrect() {
        String id = svc.recordStart("etl", T0);
        svc.recordSuccess(id, T1);
        assertThat(svc.findById(id).orElseThrow().duration())
                .isPresent()
                .hasValueSatisfying(d -> assertThat(d.toSeconds()).isEqualTo(60));
    }

    @Test
    void recordFailure_updatesStatusToFailed() {
        String id = svc.recordStart("etl", T0);
        svc.recordFailure(id, T1, "NullPointerException");
        assertThat(svc.findById(id).orElseThrow().status())
                .isEqualTo(PipelineExecutionSummary.ExecutionStatus.FAILED);
    }

    @Test
    void recordFailure_storesErrorMessage() {
        String id = svc.recordStart("etl", T0);
        svc.recordFailure(id, T1, "DB connection refused");
        assertThat(svc.findById(id).orElseThrow().errorMessage())
                .hasValue("DB connection refused");
    }

    @Test
    void recent_returnsNewestFirst() {
        String id1 = svc.recordStart("pipe", T0);
        String id2 = svc.recordStart("pipe", T1);
        assertThat(svc.recent(10).get(0).executionId()).isEqualTo(id2);
    }

    @Test
    void recent_limitsResults() {
        for (int i = 0; i < 5; i++) svc.recordStart("p", T0.plusSeconds(i));
        assertThat(svc.recent(3)).hasSize(3);
    }

    @Test
    void byPipeline_filtersCorrectly() {
        svc.recordStart("alpha", T0);
        svc.recordStart("beta",  T0);
        svc.recordStart("alpha", T1);
        assertThat(svc.byPipeline("alpha")).hasSize(2);
        assertThat(svc.byPipeline("beta")).hasSize(1);
    }

    @Test
    void findById_returnsEmpty_whenAbsent() {
        assertThat(svc.findById("nonexistent")).isEmpty();
    }

    @Test
    void size_reflectsRecordedExecutions() {
        svc.recordStart("p1", T0);
        svc.recordStart("p2", T0);
        assertThat(svc.size()).isEqualTo(2);
    }

    @Test
    void maxHistory_prunesOldEntries() {
        PipelineStatusService bounded = new PipelineStatusService(3);
        for (int i = 0; i < 5; i++) bounded.recordStart("p", T0.plusSeconds(i));
        assertThat(bounded.size()).isEqualTo(3);
    }
}
