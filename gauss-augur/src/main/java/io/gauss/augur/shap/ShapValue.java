package io.gauss.augur.shap;

/**
 * The SHAP value (contribution) of a single feature to a prediction (HU-059).
 *
 * <p>A positive {@link #impact} means the feature pushed the prediction above
 * the baseline; a negative impact means it pushed it below.
 *
 * @param featureName  name of the feature
 * @param impact       SHAP value — change in prediction attributed to this feature
 * @param rank         rank by {@code |impact|} (1 = most influential)
 */
public record ShapValue(String featureName, double impact, int rank) {

    /** Returns {@code true} if this feature pushed the prediction upward. */
    public boolean isPositive() {
        return impact > 0;
    }

    @Override
    public String toString() {
        return String.format("ShapValue[%s: %+.4f (rank %d)]", featureName, impact, rank);
    }
}
