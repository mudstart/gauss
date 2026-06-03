package io.gauss.augur.batch;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Manages the lifecycle of async batch prediction jobs (Augur module, HU-018).
 *
 * <p>Callers submit a list of inputs and a prediction function; the tracker
 * processes them asynchronously in configurable chunk sizes and stores the
 * results in-memory.  A job can be cancelled at any point before completion.
 *
 * <p>For unit tests, pass {@code Runnable::run} as the executor to get
 * synchronous, deterministic behaviour:
 * <pre>{@code
 * BatchJobTracker tracker = new BatchJobTracker(Runnable::run);
 * String jobId = tracker.submit("churn", inputs, predictor, 10);
 * // job is already COMPLETED when submit() returns
 * }</pre>
 */
public final class BatchJobTracker {

    private static final Logger LOG = Logger.getLogger(BatchJobTracker.class.getName());

    private final Executor executor;
    private final ConcurrentHashMap<String, BatchJob> jobs = new ConcurrentHashMap<>();
    private final Supplier<Instant> clock;

    // -------------------------------------------------------------------------

    public BatchJobTracker() {
        this(Executors.newCachedThreadPool(), Instant::now);
    }

    /** Accepts a custom executor (use {@code Runnable::run} for synchronous tests). */
    public BatchJobTracker(Executor executor) {
        this(executor, Instant::now);
    }

    public BatchJobTracker(Executor executor, Supplier<Instant> clock) {
        this.executor = executor;
        this.clock    = clock;
    }

    // -------------------------------------------------------------------------
    // Submission
    // -------------------------------------------------------------------------

    /**
     * Submits a batch prediction job.
     *
     * @param endpointName the logical name of the endpoint (for grouping)
     * @param inputs       the list of inputs to process
     * @param predictor    function that maps a single input to a prediction result
     * @param batchSize    maximum inputs per internal chunk (throttles memory use)
     * @return the job ID — callers poll via {@link #findJob(String)}
     */
    public <T> String submit(String endpointName,
                              List<T> inputs,
                              Function<T, Object> predictor,
                              int batchSize) {
        String jobId = UUID.randomUUID().toString();
        BatchJob job = BatchJob.pending(jobId, endpointName, inputs.size());
        jobs.put(jobId, job);

        executor.execute(() -> run(jobId, inputs, predictor, batchSize));
        return jobId;
    }

    // -------------------------------------------------------------------------
    // Lifecycle management
    // -------------------------------------------------------------------------

    /**
     * Returns the current snapshot of the job with {@code jobId}, or empty if
     * the job is unknown.
     */
    public Optional<BatchJob> findJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    /**
     * Returns all jobs associated with {@code endpointName}.
     */
    public List<BatchJob> findByEndpoint(String endpointName) {
        return jobs.values().stream()
                .filter(j -> j.endpointName().equals(endpointName))
                .toList();
    }

    /**
     * Requests cancellation of the job.  The job moves to
     * {@link BatchJobStatus#CANCELLED} as soon as the current chunk finishes.
     *
     * @return {@code true} if the job was found and was not yet terminal
     */
    public boolean cancel(String jobId) {
        BatchJob job = jobs.get(jobId);
        if (job == null || job.isTerminal()) return false;
        jobs.put(jobId, job.cancelled(clock.get()));
        return true;
    }

    /** Total number of tracked jobs (all states). */
    public int jobCount() {
        return jobs.size();
    }

    // -------------------------------------------------------------------------
    // Execution
    // -------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private <T> void run(String jobId, List<T> inputs,
                          Function<T, Object> predictor, int batchSize) {
        BatchJob job = jobs.get(jobId);
        if (job == null) return;

        jobs.put(jobId, job.running(clock.get()));

        List<Object> results = new ArrayList<>(inputs.size());
        try {
            for (int i = 0; i < inputs.size(); i += batchSize) {
                // Check for cancellation between chunks
                BatchJob current = jobs.get(jobId);
                if (current == null || current.status() == BatchJobStatus.CANCELLED) {
                    return;
                }

                List<T> chunk = inputs.subList(i, Math.min(i + batchSize, inputs.size()));
                for (T input : chunk) {
                    results.add(predictor.apply(input));
                }
                jobs.put(jobId, current.progress(results.size()));
            }

            BatchJob done = jobs.get(jobId);
            if (done != null && done.status() != BatchJobStatus.CANCELLED) {
                jobs.put(jobId, done.completed(results, clock.get()));
            }

        } catch (Exception e) {
            BatchJob failed = jobs.get(jobId);
            if (failed != null) {
                jobs.put(jobId, failed.failed(e, clock.get()));
            }
            LOG.warning(() -> "Batch job " + jobId + " failed: " + e.getMessage());
        }
    }
}
