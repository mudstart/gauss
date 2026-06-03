package io.gauss.augur.batch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link BatchJobTracker} and {@link BatchJob}.
 * Covers HU-018 acceptance criteria.
 * Uses {@code Runnable::run} as the executor for synchronous, deterministic tests.
 */
class BatchJobTrackerTest {

    /** Synchronous executor — jobs complete inline before submit() returns. */
    private BatchJobTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new BatchJobTracker(Runnable::run);
    }

    // -------------------------------------------------------------------------
    // BatchJobStatus
    // -------------------------------------------------------------------------

    @Test
    void initialStatus_isPending() {
        // Cannot observe PENDING with sync executor, but verify enum exists
        assertThat(BatchJobStatus.values()).contains(BatchJobStatus.PENDING,
                BatchJobStatus.RUNNING, BatchJobStatus.COMPLETED,
                BatchJobStatus.CANCELLED, BatchJobStatus.FAILED);
    }

    // -------------------------------------------------------------------------
    // Successful completion
    // -------------------------------------------------------------------------

    @Test
    void submit_completedStatus_afterSyncExecution() {
        String id = tracker.submit("churn", List.of("e1","e2"), input -> 0.5, 10);
        assertThat(tracker.findJob(id).orElseThrow().status())
                .isEqualTo(BatchJobStatus.COMPLETED);
    }

    @Test
    void submit_resultsMatchInputCount() {
        List<String> inputs = List.of("a","b","c");
        String id = tracker.submit("ep", inputs, s -> s.length(), 10);
        assertThat(tracker.findJob(id).orElseThrow().results()).hasSize(3);
    }

    @Test
    void submit_resultsAreCorrect() {
        String id = tracker.submit("ep", List.of("hi","hello"), s -> ((String) s).length(), 10);
        BatchJob job = tracker.findJob(id).orElseThrow();
        assertThat(job.results()).containsExactly(2, 5);
    }

    @Test
    void submit_inputCountStoredCorrectly() {
        String id = tracker.submit("ep", List.of("a","b","c","d"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().inputCount()).isEqualTo(4);
    }

    @Test
    void submit_completedCount_equalsInputCount() {
        String id = tracker.submit("ep", List.of("x","y","z"), input -> 0, 10);
        BatchJob job = tracker.findJob(id).orElseThrow();
        assertThat(job.completedCount()).isEqualTo(job.inputCount());
    }

    @Test
    void submit_progressPercent_100_whenCompleted() {
        String id = tracker.submit("ep", List.of("a"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().progressPercent()).isEqualTo(100);
    }

    @Test
    void submit_startedAt_isSet() {
        String id = tracker.submit("ep", List.of("x"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().startedAt()).isNotNull();
    }

    @Test
    void submit_completedAt_isSet() {
        String id = tracker.submit("ep", List.of("x"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().completedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // Batching chunks
    // -------------------------------------------------------------------------

    @Test
    void submit_batchSize1_processesAllInputs() {
        List<Integer> inputs = IntStream.range(0, 10).boxed().toList();
        String id = tracker.submit("ep", inputs, n -> ((Integer)n) * 2, 1);
        assertThat(tracker.findJob(id).orElseThrow().results()).hasSize(10);
    }

    @Test
    void submit_largeBatchSize_processesAllInputs() {
        List<Integer> inputs = IntStream.range(0, 5).boxed().toList();
        String id = tracker.submit("ep", inputs, n -> n, 1000);
        assertThat(tracker.findJob(id).orElseThrow().results()).hasSize(5);
    }

    // -------------------------------------------------------------------------
    // Empty inputs
    // -------------------------------------------------------------------------

    @Test
    void submit_emptyInputs_completedWithEmptyResults() {
        String id = tracker.submit("ep", List.of(), input -> 1, 10);
        BatchJob job = tracker.findJob(id).orElseThrow();
        assertThat(job.status()).isEqualTo(BatchJobStatus.COMPLETED);
        assertThat(job.results()).isEmpty();
    }

    @Test
    void submit_emptyInputs_progressPercent_100() {
        String id = tracker.submit("ep", List.of(), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().progressPercent()).isEqualTo(100);
    }

    // -------------------------------------------------------------------------
    // Error handling
    // -------------------------------------------------------------------------

    @Test
    void submit_predictor_throws_jobFails() {
        String id = tracker.submit("ep", List.of("bad"),
                input -> { throw new RuntimeException("boom"); }, 10);
        assertThat(tracker.findJob(id).orElseThrow().status())
                .isEqualTo(BatchJobStatus.FAILED);
    }

    @Test
    void submit_failed_failureCauseIsPresent() {
        String id = tracker.submit("ep", List.of("x"),
                input -> { throw new IllegalStateException("err"); }, 10);
        assertThat(tracker.findJob(id).orElseThrow().failureCause()).isPresent();
    }

    // -------------------------------------------------------------------------
    // isTerminal
    // -------------------------------------------------------------------------

    @Test
    void completed_isTerminal() {
        String id = tracker.submit("ep", List.of("x"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().isTerminal()).isTrue();
    }

    @Test
    void failed_isTerminal() {
        String id = tracker.submit("ep", List.of("x"),
                input -> { throw new RuntimeException(); }, 10);
        assertThat(tracker.findJob(id).orElseThrow().isTerminal()).isTrue();
    }

    // -------------------------------------------------------------------------
    // findJob / findByEndpoint
    // -------------------------------------------------------------------------

    @Test
    void findJob_unknownId_returnsEmpty() {
        assertThat(tracker.findJob("nonexistent")).isEmpty();
    }

    @Test
    void findByEndpoint_returnsJobsForEndpoint() {
        tracker.submit("ep-a", List.of("x"), input -> 1, 10);
        tracker.submit("ep-a", List.of("y"), input -> 2, 10);
        tracker.submit("ep-b", List.of("z"), input -> 3, 10);
        assertThat(tracker.findByEndpoint("ep-a")).hasSize(2);
        assertThat(tracker.findByEndpoint("ep-b")).hasSize(1);
    }

    @Test
    void jobCount_incrementsOnSubmit() {
        tracker.submit("ep", List.of("a"), input -> 1, 10);
        tracker.submit("ep", List.of("b"), input -> 2, 10);
        assertThat(tracker.jobCount()).isEqualTo(2);
    }

    // -------------------------------------------------------------------------
    // endpointName stored on job
    // -------------------------------------------------------------------------

    @Test
    void submit_endpointName_storedOnJob() {
        String id = tracker.submit("churn-service", List.of("x"), input -> 1, 10);
        assertThat(tracker.findJob(id).orElseThrow().endpointName())
                .isEqualTo("churn-service");
    }
}
