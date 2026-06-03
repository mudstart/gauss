package io.gauss.flume.status;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tracks and exposes pipeline execution state for the admin dashboard (HU-014).
 *
 * <p>The service provides SSE-ready status snapshots that can be served via a
 * REST endpoint.  Executions are stored in insertion order and the list
 * is pruned to the configured maximum capacity to avoid unbounded memory use.
 */
public final class PipelineStatusService {

    private static final int DEFAULT_MAX_HISTORY = 1_000;

    private final CopyOnWriteArrayList<PipelineExecutionSummary> history =
            new CopyOnWriteArrayList<>();
    private final int maxHistory;

    public PipelineStatusService() {
        this(DEFAULT_MAX_HISTORY);
    }

    public PipelineStatusService(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    // -------------------------------------------------------------------------

    /** Records the start of a new pipeline execution. Returns the execution ID. */
    public String recordStart(String pipelineName, Instant startedAt) {
        String id = UUID.randomUUID().toString();
        history.add(new PipelineExecutionSummary(
                id, pipelineName,
                PipelineExecutionSummary.ExecutionStatus.RUNNING,
                startedAt, Optional.empty(), Optional.empty()));
        pruneIfNeeded();
        return id;
    }

    /** Records successful completion of an execution. */
    public void recordSuccess(String executionId, Instant finishedAt) {
        replace(executionId, existing -> new PipelineExecutionSummary(
                existing.executionId(), existing.pipelineName(),
                PipelineExecutionSummary.ExecutionStatus.COMPLETED,
                existing.startedAt(), Optional.of(finishedAt), Optional.empty()));
    }

    /** Records a failed execution with a descriptive error message. */
    public void recordFailure(String executionId, Instant finishedAt, String errorMessage) {
        replace(executionId, existing -> new PipelineExecutionSummary(
                existing.executionId(), existing.pipelineName(),
                PipelineExecutionSummary.ExecutionStatus.FAILED,
                existing.startedAt(), Optional.of(finishedAt),
                Optional.of(errorMessage)));
    }

    // -------------------------------------------------------------------------

    /** Returns the most recent {@code limit} executions, newest first. */
    public List<PipelineExecutionSummary> recent(int limit) {
        return history.stream()
                .sorted(Comparator.comparing(PipelineExecutionSummary::startedAt).reversed())
                .limit(limit)
                .toList();
    }

    /** Returns all executions for the named pipeline, newest first. */
    public List<PipelineExecutionSummary> byPipeline(String pipelineName) {
        return history.stream()
                .filter(e -> e.pipelineName().equals(pipelineName))
                .sorted(Comparator.comparing(PipelineExecutionSummary::startedAt).reversed())
                .toList();
    }

    /** Finds an execution by its ID. */
    public Optional<PipelineExecutionSummary> findById(String executionId) {
        return history.stream()
                .filter(e -> e.executionId().equals(executionId))
                .findFirst();
    }

    /** Total number of recorded executions. */
    public int size() {
        return history.size();
    }

    // -------------------------------------------------------------------------

    private void replace(String executionId,
                          java.util.function.Function<PipelineExecutionSummary,
                                  PipelineExecutionSummary> updater) {
        for (int i = 0; i < history.size(); i++) {
            if (history.get(i).executionId().equals(executionId)) {
                history.set(i, updater.apply(history.get(i)));
                return;
            }
        }
    }

    private void pruneIfNeeded() {
        while (history.size() > maxHistory) {
            history.remove(0);
        }
    }
}
