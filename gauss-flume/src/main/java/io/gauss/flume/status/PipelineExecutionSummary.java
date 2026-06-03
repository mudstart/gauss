package io.gauss.flume.status;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Immutable snapshot of a single pipeline execution (HU-014).
 *
 * @param executionId  unique ID for this run
 * @param pipelineName the {@code @DataPipeline} name
 * @param status       current or final execution state
 * @param startedAt    when execution began
 * @param finishedAt   when execution ended (empty if still running)
 * @param errorMessage error description for FAILED runs
 */
public record PipelineExecutionSummary(
        String             executionId,
        String             pipelineName,
        ExecutionStatus    status,
        Instant            startedAt,
        Optional<Instant>  finishedAt,
        Optional<String>   errorMessage
) {

    public PipelineExecutionSummary {
        finishedAt   = finishedAt   == null ? Optional.empty() : finishedAt;
        errorMessage = errorMessage == null ? Optional.empty() : errorMessage;
    }

    /** Returns elapsed or total duration, or empty for runs not yet started. */
    public Optional<Duration> duration() {
        return finishedAt.map(end -> Duration.between(startedAt, end));
    }

    public enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, CANCELLED
    }
}
