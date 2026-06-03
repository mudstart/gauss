package io.gauss.vigil.rollback;

import io.gauss.vigil.registry.ModelRegistration;
import io.gauss.vigil.registry.ModelRegistry;
import io.gauss.vigil.registry.Stage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Monitors per-model metric observations and triggers automatic rollbacks when
 * a sliding-window average exceeds the configured threshold (Vigil module, HU-054).
 *
 * <p>Rollback semantics:
 * <ol>
 *   <li>Call {@link #recordMetric(String, String, double)} after each prediction.</li>
 *   <li>Call {@link #evaluate(String, RollbackPolicy)} periodically (or after each
 *       {@code recordMetric}) to check whether the threshold is breached.</li>
 *   <li>If a rollback is needed, the service promotes the most recent
 *       {@link Stage#ARCHIVED} or second-to-last {@link Stage#PRODUCTION}
 *       registration with the same model name.</li>
 * </ol>
 *
 * <p>A circuit-breaker on the rollback frequency prevents oscillation:
 * no more than {@link RollbackPolicy#maxPerHour()} rollbacks are executed
 * per hour per model.
 *
 * <p>Usage:
 * <pre>{@code
 * RollbackService svc   = new RollbackService();
 * RollbackPolicy policy = RollbackPolicy.of("error_rate", 0.15, 10, 3);
 *
 * svc.recordMetric(modelId, "error_rate", 0.22);  // above threshold
 * Optional<RollbackEvent> event = svc.evaluate(modelId, policy);
 * event.ifPresent(e -> log.warn(e.summary()));
 * }</pre>
 */
public final class RollbackService {

    private static final Logger LOG = Logger.getLogger(RollbackService.class.getName());

    // metric observations: modelId → list of (value, recordedAt)
    private final Map<String, List<TimedObservation>> observations = new ConcurrentHashMap<>();
    // rollback timestamps for rate-limiting
    private final Map<String, List<Instant>>          rollbackHistory = new ConcurrentHashMap<>();
    // audit log
    private final List<RollbackEvent>                 auditLog = new ArrayList<>();

    private final Supplier<Instant> clock;

    public RollbackService() {
        this(Instant::now);
    }

    /** Test constructor — injects a custom clock for deterministic time. */
    public RollbackService(Supplier<Instant> clock) {
        this.clock = clock;
    }

    // -------------------------------------------------------------------------
    // Metric recording
    // -------------------------------------------------------------------------

    /**
     * Records a single metric observation for the model identified by {@code modelId}.
     *
     * @param modelId    the model registration ID
     * @param metricName the name of the metric (must match
     *                   {@link RollbackPolicy#metricName()})
     * @param value      the observed metric value
     */
    public void recordMetric(String modelId, String metricName, double value) {
        observations.computeIfAbsent(modelId, k -> new ArrayList<>())
                .add(new TimedObservation(metricName, value, clock.get()));
    }

    // -------------------------------------------------------------------------
    // Evaluation
    // -------------------------------------------------------------------------

    /**
     * Evaluates the rollback policy for the given model.
     *
     * <p>Returns a {@link RollbackEvent} if:
     * <ol>
     *   <li>The sliding-window average of the policy metric exceeds the threshold.</li>
     *   <li>The rate limit ({@link RollbackPolicy#maxPerHour()}) has not been reached.</li>
     *   <li>A previous production version exists to roll back to.</li>
     * </ol>
     *
     * @param modelId the model to evaluate
     * @param policy  the rollback policy to apply
     * @return a rollback event if rollback was executed, or empty
     */
    public Optional<RollbackEvent> evaluate(String modelId, RollbackPolicy policy) {
        // 1. Check if threshold is exceeded in the window
        double windowAvg = windowAverage(modelId, policy);
        if (windowAvg <= policy.threshold()) {
            return Optional.empty();
        }

        // 3. Find current model (needed for name-based rate limit)
        ModelRegistration current = ModelRegistry.find(modelId).orElse(null);
        if (current == null) return Optional.empty();

        // 2. Check rate limit by model NAME (not ID) to cover version changes
        if (isRateLimited(current.modelName(), policy.maxPerHour())) {
            LOG.warning(() -> "Rollback rate limit reached for model " + current.modelName());
            return Optional.empty();
        }

        Optional<ModelRegistration> predecessor = findPredecessor(current.modelName(), modelId);
        if (predecessor.isEmpty()) {
            LOG.warning(() -> "No predecessor found for rollback of model " + modelId);
            return Optional.empty();
        }

        // 4. Execute rollback
        ModelRegistration prev = predecessor.get();
        ModelRegistry.promote(prev.id(), Stage.PRODUCTION, "auto-rollback");
        ModelRegistry.promote(modelId,   Stage.ARCHIVED,   "auto-rollback");

        RollbackEvent event = new RollbackEvent(
                modelId, current.modelName(),
                policy.metricName(), windowAvg, policy.threshold(),
                prev.id(), clock.get());

        recordRollback(current.modelName());
        auditLog.add(event);
        LOG.warning(event::summary);

        return Optional.of(event);
    }

    // -------------------------------------------------------------------------
    // Audit log
    // -------------------------------------------------------------------------

    /** Returns all rollback events since this service was created. */
    public List<RollbackEvent> auditLog() {
        return List.copyOf(auditLog);
    }

    /** Clears all observations and history (for tests). */
    public void reset() {
        observations.clear();
        rollbackHistory.clear();
        auditLog.clear();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private double windowAverage(String modelId, RollbackPolicy policy) {
        List<TimedObservation> all = observations.getOrDefault(modelId, List.of());
        Instant cutoff = clock.get().minusSeconds(policy.windowMinutes() * 60L);
        return all.stream()
                .filter(o -> o.name().equals(policy.metricName()))
                .filter(o -> !o.recordedAt().isBefore(cutoff))
                .mapToDouble(TimedObservation::value)
                .average()
                .orElse(0.0);
    }

    private boolean isRateLimited(String modelId, int maxPerHour) {
        List<Instant> history = rollbackHistory.getOrDefault(modelId, List.of());
        Instant cutoff = clock.get().minusSeconds(3600);
        long recentCount = history.stream().filter(t -> !t.isBefore(cutoff)).count();
        return recentCount >= maxPerHour;
    }

    private void recordRollback(String modelId) {
        rollbackHistory.computeIfAbsent(modelId, k -> new ArrayList<>()).add(clock.get());
    }

    /** Returns the most recent non-current PRODUCTION or ARCHIVED registration. */
    private static Optional<ModelRegistration> findPredecessor(String modelName,
                                                                 String excludeId) {
        return ModelRegistry.findAll().stream()
                .filter(r -> r.modelName().equals(modelName))
                .filter(r -> !r.id().equals(excludeId))
                .filter(r -> r.currentStage() == Stage.PRODUCTION
                          || r.currentStage() == Stage.STAGING)
                .reduce((first, second) -> second);  // last registered
    }

    // -------------------------------------------------------------------------
    // Inner record for time-stamped observations
    // -------------------------------------------------------------------------

    private record TimedObservation(String name, double value, Instant recordedAt) {}
}
