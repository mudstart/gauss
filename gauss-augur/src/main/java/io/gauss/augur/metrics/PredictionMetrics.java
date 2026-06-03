package io.gauss.augur.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * Records Micrometer metrics for every ML model inference call.
 *
 * <p>Two meters are registered per model:
 * <ul>
 *   <li>{@code dsml.prediction.latency} ({@link Timer}) — wall-clock time
 *       of the inference call, tagged with {@code model} and {@code status}
 *       ({@code success} or {@code error}).</li>
 *   <li>{@code dsml.prediction.count} ({@link Counter}) — cumulative count
 *       of calls, tagged with the same labels.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * PredictionMetrics metrics = new PredictionMetrics(meterRegistry);
 *
 * float[] result = metrics.record("churn-model",
 *     () -> model.predict(features, float[].class));
 * }</pre>
 */
public class PredictionMetrics {

    /** Meter name for prediction latency. */
    public static final String LATENCY_METRIC  = "dsml.prediction.latency";

    /** Meter name for prediction call count. */
    public static final String COUNT_METRIC    = "dsml.prediction.count";

    /** Tag key for the model identifier. */
    public static final String TAG_MODEL       = "model";

    /** Tag key for call outcome. */
    public static final String TAG_STATUS      = "status";

    /** Tag value for successful calls. */
    public static final String STATUS_SUCCESS  = "success";

    /** Tag value for calls that threw an exception. */
    public static final String STATUS_ERROR    = "error";

    private final MeterRegistry registry;

    // Cache timers and counters per (model, status) to avoid repeated lookups
    private final ConcurrentMap<String, Timer>   timers   = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Counter> counters = new ConcurrentHashMap<>();

    public PredictionMetrics(MeterRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        this.registry = registry;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Executes {@code inferenceCall}, records its latency and outcome, and
     * returns the produced value.
     *
     * @param modelName     identifies the model (used as the {@code model} tag)
     * @param inferenceCall the inference logic to time
     * @param <T>           return type of the inference
     * @return value returned by {@code inferenceCall}
     * @throws RuntimeException if {@code inferenceCall} throws; the exception
     *         is re-thrown after recording an {@code error} status metric
     */
    public <T> T record(String modelName, Supplier<T> inferenceCall) {
        Timer.Sample sample = Timer.start(registry);
        String status = STATUS_SUCCESS;
        try {
            return inferenceCall.get();
        } catch (RuntimeException e) {
            status = STATUS_ERROR;
            throw e;
        } finally {
            String finalStatus = status;
            sample.stop(timer(modelName, finalStatus));
            counter(modelName, finalStatus).increment();
        }
    }

    /**
     * Convenience overload for void inference calls (e.g. warm-up).
     *
     * @param modelName     model identifier
     * @param inferenceCall the inference logic to time
     */
    public void record(String modelName, Runnable inferenceCall) {
        record(modelName, () -> {
            inferenceCall.run();
            return null;
        });
    }

    /**
     * Returns the {@link Timer} registered for the given model and status.
     * Creates it on first access.
     */
    public Timer timer(String modelName, String status) {
        return timers.computeIfAbsent(
                cacheKey(modelName, status),
                k -> Timer.builder(LATENCY_METRIC)
                        .description("Latency of ML inference calls")
                        .tag(TAG_MODEL, modelName)
                        .tag(TAG_STATUS, status)
                        .register(registry));
    }

    /**
     * Returns the {@link Counter} registered for the given model and status.
     * Creates it on first access.
     */
    public Counter counter(String modelName, String status) {
        return counters.computeIfAbsent(
                cacheKey(modelName, status),
                k -> Counter.builder(COUNT_METRIC)
                        .description("Number of ML inference calls")
                        .tag(TAG_MODEL, modelName)
                        .tag(TAG_STATUS, status)
                        .register(registry));
    }

    // -------------------------------------------------------------------------

    private static String cacheKey(String model, String status) {
        return model + ":" + status;
    }
}
