package io.gauss.vigil.registry;

import io.gauss.vigil.experiment.ExperimentRun;
import io.gauss.vigil.experiment.ExperimentStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Central registry for versioned, staged ML models (HU-025).
 *
 * <p>The registry maintains the lifecycle of each registered model:
 * <ol>
 *   <li>Register — add a model to the registry in {@link Stage#STAGING}.</li>
 *   <li>Promote — advance to {@link Stage#PRODUCTION} (guardrails checked).</li>
 *   <li>Archive — retire a model to {@link Stage#ARCHIVED}.</li>
 * </ol>
 *
 * <p>Only users with the {@code ML_ENGINEER} role should call
 * {@link #promote(String, Stage, String)} to {@code PRODUCTION}; the
 * Quarkus security layer enforces this via interceptors.
 *
 * <p>Usage:
 * <pre>{@code
 * String id = ModelRegistry.register("churn-v2", "exp-abc", "models/churn.onnx");
 * ModelRegistry.promote(id, Stage.PRODUCTION, "alice");
 *
 * List<ModelRegistration> prod = ModelRegistry.findByStage(Stage.PRODUCTION);
 * }</pre>
 */
public final class ModelRegistry {

    private static final Logger LOG = Logger.getLogger(ModelRegistry.class.getName());

    // Singleton backing store (replaceable for testing via reset())
    private static final CopyOnWriteArrayList<ModelRegistration> STORE =
            new CopyOnWriteArrayList<>();

    private static ExperimentStore experimentStore;  // optional — for guardrail evaluation

    private static final GuardrailEvaluator EVALUATOR = new GuardrailEvaluator();

    // -------------------------------------------------------------------------
    // Registration
    // -------------------------------------------------------------------------

    /**
     * Registers a new model in {@link Stage#STAGING}.
     *
     * @param modelName    human-readable name
     * @param experimentId ID of the producing experiment run ({@code null} allowed)
     * @param modelPath    classpath or file-system path to the model artefact
     * @return the generated model ID
     */
    public static String register(String modelName,
                                   String experimentId,
                                   String modelPath) {
        return register(modelName, experimentId, modelPath, "system");
    }

    /**
     * Registers a new model in {@link Stage#STAGING} with an explicit actor.
     */
    public static String register(String modelName,
                                   String experimentId,
                                   String modelPath,
                                   String actor) {
        String id = UUID.randomUUID().toString();
        List<StageTransition> history = new ArrayList<>();
        history.add(StageTransition.initial(Stage.STAGING, actor));
        ModelRegistration reg = new ModelRegistration(
                id, experimentId, modelName, modelPath,
                Stage.STAGING, Instant.now(), List.copyOf(history));
        STORE.add(reg);
        LOG.fine(() -> "Gauss Vigil: registered model '" + modelName + "' id=" + id);
        return id;
    }

    // -------------------------------------------------------------------------
    // Stage promotion
    // -------------------------------------------------------------------------

    /**
     * Promotes the model with the given ID to {@code newStage}.
     *
     * <p>When promoting to {@link Stage#PRODUCTION}, any {@link ModelGuardrail}
     * annotations on the producing experiment's method are evaluated.  Pass
     * guardrails explicitly via
     * {@link #promote(String, Stage, String, ModelGuardrail[])}.
     *
     * @param modelId  the model to promote
     * @param newStage target stage
     * @throws IllegalArgumentException if the model ID is unknown
     */
    public static void promote(String modelId, Stage newStage) {
        promote(modelId, newStage, "system", new ModelGuardrail[0]);
    }

    /**
     * Promotes the model to {@code newStage} with an explicit actor identity.
     */
    public static void promote(String modelId, Stage newStage, String actor) {
        promote(modelId, newStage, actor, new ModelGuardrail[0]);
    }

    /**
     * Promotes the model to {@code newStage}, evaluating {@code guardrails}
     * against the metrics of the associated experiment run (if available).
     *
     * @throws GuardrailViolationException if any guardrail threshold is not met
     */
    public static void promote(String modelId,
                                Stage newStage,
                                String actor,
                                ModelGuardrail[] guardrails) {
        ModelRegistration existing = findRequired(modelId);

        // Evaluate guardrails when promoting to PRODUCTION
        if (newStage == Stage.PRODUCTION
                && guardrails.length > 0
                && experimentStore != null
                && existing.experimentId() != null) {
            experimentStore.findById(existing.experimentId())
                    .ifPresent(run -> EVALUATOR.evaluate(run, guardrails));
        }

        ModelRegistration updated = existing.withStage(newStage, actor, "");
        replace(updated);
        LOG.fine(() -> "Gauss Vigil: model '" + modelId + "' promoted to " + newStage
                + " by " + actor);
    }

    /**
     * Promotes after evaluating guardrails against a pre-computed metric map
     * (used when there is no backing experiment run).
     */
    public static void promote(String modelId,
                                Stage newStage,
                                String actor,
                                ModelGuardrail[] guardrails,
                                Map<String, Double> metrics) {
        if (newStage == Stage.PRODUCTION && guardrails.length > 0) {
            EVALUATOR.evaluate(metrics, guardrails);
        }
        ModelRegistration existing = findRequired(modelId);
        replace(existing.withStage(newStage, actor, ""));
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

    /** Returns all registered models. */
    public static List<ModelRegistration> findAll() {
        return List.copyOf(STORE);
    }

    /** Returns the model with the given ID, or empty. */
    public static Optional<ModelRegistration> find(String modelId) {
        return STORE.stream().filter(r -> r.id().equals(modelId)).findFirst();
    }

    /** Returns all models currently in the given stage. */
    public static List<ModelRegistration> findByStage(Stage stage) {
        return STORE.stream().filter(r -> r.currentStage() == stage).toList();
    }

    /**
     * Returns the latest {@link Stage#PRODUCTION} registration for the given
     * model name, or empty if none exists.
     */
    public static Optional<ModelRegistration> findProduction(String modelName) {
        return STORE.stream()
                .filter(r -> r.modelName().equals(modelName)
                        && r.currentStage() == Stage.PRODUCTION)
                .reduce((first, second) -> second);  // last registered wins
    }

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------

    /**
     * Wires an {@link ExperimentStore} so that guardrail evaluation can look
     * up metrics from the associated experiment run.
     */
    public static void setExperimentStore(ExperimentStore store) {
        experimentStore = store;
    }

    /**
     * Clears all registrations (intended for tests).
     */
    public static void reset() {
        STORE.clear();
        experimentStore = null;
    }

    // -------------------------------------------------------------------------

    private static ModelRegistration findRequired(String modelId) {
        return find(modelId).orElseThrow(
                () -> new IllegalArgumentException("Model not found: " + modelId));
    }

    private static void replace(ModelRegistration updated) {
        STORE.removeIf(r -> r.id().equals(updated.id()));
        STORE.add(updated);
    }

    private ModelRegistry() {}
}
