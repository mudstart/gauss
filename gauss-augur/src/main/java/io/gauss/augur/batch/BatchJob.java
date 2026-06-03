package io.gauss.augur.batch;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Immutable snapshot of a batch prediction job at a point in time (HU-018).
 *
 * <p>{@link BatchJobTracker} replaces the stored instance atomically whenever
 * the job advances through its lifecycle.
 *
 * @param id             UUID assigned at submission time
 * @param endpointName   the {@code @MLEndpoint} that owns this job
 * @param status         current lifecycle state
 * @param inputCount     total number of inputs submitted
 * @param completedCount inputs processed so far (updated during RUNNING)
 * @param results        prediction outputs (populated on COMPLETED)
 * @param startedAt      when the job moved to RUNNING
 * @param completedAt    when the job reached a terminal state
 * @param failureCause   the exception that caused FAILED state
 */
public record BatchJob(
        String           id,
        String           endpointName,
        BatchJobStatus   status,
        int              inputCount,
        int              completedCount,
        List<Object>     results,
        Instant          startedAt,
        Instant          completedAt,
        Optional<Throwable> failureCause
) {

    public BatchJob {
        results = results == null ? List.of() : List.copyOf(results);
    }

    /** Progress percentage 0–100. */
    public int progressPercent() {
        if (inputCount == 0) return 100;
        return 100 * completedCount / inputCount;
    }

    /** Returns {@code true} if the job has reached a terminal state. */
    public boolean isTerminal() {
        return status == BatchJobStatus.COMPLETED
            || status == BatchJobStatus.CANCELLED
            || status == BatchJobStatus.FAILED;
    }

    // -------------------------------------------------------------------------
    // Internal factories used by BatchJobTracker
    // -------------------------------------------------------------------------

    static BatchJob pending(String id, String endpointName, int inputCount) {
        return new BatchJob(id, endpointName, BatchJobStatus.PENDING,
                inputCount, 0, List.of(), null, null, Optional.empty());
    }

    BatchJob running(Instant startedAt) {
        return new BatchJob(id, endpointName, BatchJobStatus.RUNNING,
                inputCount, 0, List.of(), startedAt, null, Optional.empty());
    }

    BatchJob progress(int completedCount) {
        return new BatchJob(id, endpointName, BatchJobStatus.RUNNING,
                inputCount, completedCount, List.of(), startedAt, null, Optional.empty());
    }

    BatchJob completed(List<Object> results, Instant completedAt) {
        return new BatchJob(id, endpointName, BatchJobStatus.COMPLETED,
                inputCount, inputCount, results, startedAt, completedAt, Optional.empty());
    }

    BatchJob cancelled(Instant completedAt) {
        return new BatchJob(id, endpointName, BatchJobStatus.CANCELLED,
                inputCount, completedCount, List.of(), startedAt, completedAt, Optional.empty());
    }

    BatchJob failed(Throwable cause, Instant completedAt) {
        return new BatchJob(id, endpointName, BatchJobStatus.FAILED,
                inputCount, completedCount, List.of(), startedAt, completedAt,
                Optional.of(cause));
    }
}
