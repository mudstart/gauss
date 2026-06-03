package io.gauss.vigil.registry;

import java.time.Instant;

/**
 * An immutable record of a single stage transition for a registered model.
 *
 * @param fromStage  stage before the transition ({@code null} for the initial registration)
 * @param toStage    stage after the transition
 * @param actor      user or system identity that triggered the transition
 * @param timestamp  wall-clock time of the transition
 * @param reason     optional free-text reason (may be empty)
 */
public record StageTransition(
        Stage   fromStage,
        Stage   toStage,
        String  actor,
        Instant timestamp,
        String  reason
) {

    public static StageTransition of(Stage from, Stage to, String actor, String reason) {
        return new StageTransition(from, to, actor, Instant.now(), reason);
    }

    public static StageTransition initial(Stage initialStage, String actor) {
        return new StageTransition(null, initialStage, actor, Instant.now(), "initial registration");
    }
}
