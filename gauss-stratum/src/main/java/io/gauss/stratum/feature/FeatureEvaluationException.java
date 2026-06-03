package io.gauss.stratum.feature;

/**
 * Thrown by {@link FeatureEvaluator} when a feature computation fails.
 */
public class FeatureEvaluationException extends RuntimeException {

    private final String featureName;
    private final String entityId;

    public FeatureEvaluationException(String featureName, String entityId, Throwable cause) {
        super("Failed to evaluate feature '" + featureName
                + "' for entity '" + entityId + "': " + cause.getMessage(), cause);
        this.featureName = featureName;
        this.entityId    = entityId;
    }

    public String featureName() { return featureName; }
    public String entityId()    { return entityId;    }
}
