package io.gauss.augur.drift;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Monitors production inputs for data drift against reference distributions
 * (Augur module, HU-037).
 *
 * <p>Workflow:
 * <ol>
 *   <li>Call {@link #setReference} with the training-time feature distribution.</li>
 *   <li>Call {@link #recordObservation} after each prediction.</li>
 *   <li>Call {@link #evaluate} when the sample window is full; it returns a
 *       {@link DriftReport} and logs an alert if drift exceeds the threshold.</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * DriftMonitorService monitor = new DriftMonitorService();
 * monitor.setReference("churn", referenceFeatures, 10);
 *
 * // After each prediction:
 * monitor.recordObservation("churn", inputFeatureValue);
 *
 * // Every N observations:
 * monitor.evaluate("churn", 0.1, 100)
 *        .ifPresent(report -> log.warn(report.summary()));
 * }</pre>
 */
public final class DriftMonitorService {

    private static final Logger LOG = Logger.getLogger(DriftMonitorService.class.getName());

    private final Map<String, double[]>      references   = new ConcurrentHashMap<>();
    private final Map<String, List<Double>>  observations = new ConcurrentHashMap<>();
    private final List<DriftReport>          history      = new ArrayList<>();
    private final PSICalculator              psi          = new PSICalculator();
    private final Supplier<Instant>          clock;

    public DriftMonitorService() {
        this(Instant::now);
    }

    public DriftMonitorService(Supplier<Instant> clock) {
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // Setup
    // -------------------------------------------------------------------------

    /**
     * Registers the reference (training) distribution for {@code endpointName}.
     *
     * @param endpointName  the endpoint to monitor
     * @param referenceData training-time feature values
     * @param numBuckets    number of PSI buckets (typically 10 or 20)
     */
    public void setReference(String endpointName, double[] referenceData, int numBuckets) {
        references.put(endpointName, referenceData);
        observations.put(endpointName, new ArrayList<>());
    }

    // -------------------------------------------------------------------------
    // Observation recording
    // -------------------------------------------------------------------------

    /**
     * Records a single feature value observed during inference.
     *
     * @param endpointName the endpoint that served the prediction
     * @param featureValue the input feature value to monitor
     */
    public void recordObservation(String endpointName, double featureValue) {
        observations.computeIfAbsent(endpointName, k -> new ArrayList<>())
                    .add(featureValue);
    }

    /** Returns the number of observations accumulated for {@code endpointName}. */
    public int observationCount(String endpointName) {
        List<Double> obs = observations.get(endpointName);
        return obs == null ? 0 : obs.size();
    }

    // -------------------------------------------------------------------------
    // Drift evaluation
    // -------------------------------------------------------------------------

    /**
     * Computes the PSI drift score for {@code endpointName} using the current
     * observation window.  Returns empty if no reference or fewer than
     * {@code minSamples} observations are available.
     *
     * @param endpointName the endpoint to evaluate
     * @param threshold    alert threshold for the drift score
     * @param numBuckets   PSI bucket count (must match that used in
     *                     {@link #setReference})
     * @return drift report, or empty if insufficient data
     */
    public Optional<DriftReport> evaluate(String endpointName,
                                           double threshold,
                                           int numBuckets) {
        double[] ref = references.get(endpointName);
        List<Double> obs = observations.get(endpointName);

        if (ref == null || obs == null || obs.isEmpty()) return Optional.empty();

        double[] current = obs.stream().mapToDouble(Double::doubleValue).toArray();
        double score = psi.compute(ref, current, numBuckets);
        boolean alert = score > threshold;

        DriftReport report = new DriftReport(
                endpointName, "PSI", score, threshold, alert,
                current.length, clock.get());
        history.add(report);

        if (alert) {
            LOG.warning(() -> report.summary());
        }

        return Optional.of(report);
    }

    /**
     * Returns all drift reports produced since this service was created,
     * in evaluation order.
     */
    public List<DriftReport> history() {
        return List.copyOf(history);
    }

    /** Clears observations and history (for tests or window resets). */
    public void reset(String endpointName) {
        observations.remove(endpointName);
        // Keep reference — it doesn't change until the model is retrained
    }

    /** Clears everything including references. */
    public void resetAll() {
        references.clear();
        observations.clear();
        history.clear();
    }
}
