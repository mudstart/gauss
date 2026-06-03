package io.gauss.spark.runner;

import java.time.Duration;

/**
 * Outcome of a Spark (or local) pipeline execution (HU-015).
 *
 * @param pipelineName    the executed pipeline identifier
 * @param recordsRead     total records ingested across all {@code @Ingest} steps
 * @param recordsWritten  total records produced by the last {@code @Transform}
 * @param duration        wall-clock time of the complete execution
 * @param executedLocally {@code true} when Spark was unavailable and the job
 *                        fell back to local JVM execution
 */
public record SparkJobResult(
        String   pipelineName,
        long     recordsRead,
        long     recordsWritten,
        Duration duration,
        boolean  executedLocally
) {

    /** Returns a human-readable execution summary. */
    public String summary() {
        String mode = executedLocally ? "local" : "spark";
        return String.format("[%s] pipeline=%s read=%d written=%d time=%dms mode=%s",
                executedLocally ? "LOCAL" : "SPARK",
                pipelineName, recordsRead, recordsWritten,
                duration.toMillis(), mode);
    }
}
