package io.gauss.vigil.registry;

import java.time.Instant;
import java.util.List;

/**
 * Immutable snapshot of a model's current registration in the Model Registry
 * (HU-025).
 *
 * @param id            unique model identifier
 * @param experimentId  ID of the {@link io.gauss.vigil.experiment.ExperimentRun}
 *                      that produced this model ({@code null} if registered
 *                      without an experiment)
 * @param modelName     human-readable model name (e.g. {@code "churn-xgboost"})
 * @param modelPath     classpath or file-system path to the model artefact
 * @param currentStage  current lifecycle stage
 * @param registeredAt  timestamp when the model was first registered
 * @param history       ordered list of all stage transitions (most recent last)
 */
public record ModelRegistration(
        String               id,
        String               experimentId,
        String               modelName,
        String               modelPath,
        Stage                currentStage,
        Instant              registeredAt,
        List<StageTransition> history
) {

    /**
     * Returns a copy of this registration with the stage updated to
     * {@code newStage} and the transition appended to the history.
     */
    public ModelRegistration withStage(Stage newStage, String actor, String reason) {
        StageTransition transition = StageTransition.of(currentStage, newStage, actor, reason);
        List<StageTransition> newHistory = new java.util.ArrayList<>(history);
        newHistory.add(transition);
        return new ModelRegistration(
                id, experimentId, modelName, modelPath,
                newStage, registeredAt, List.copyOf(newHistory));
    }

    /**
     * Returns the last stage transition, or {@code null} for a freshly
     * registered model.
     */
    public StageTransition lastTransition() {
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }
}
