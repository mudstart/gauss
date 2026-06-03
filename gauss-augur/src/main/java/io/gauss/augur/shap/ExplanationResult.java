package io.gauss.augur.shap;

import java.util.List;

/**
 * The complete SHAP explanation for a single prediction (HU-059).
 *
 * <p>The relationship between the fields satisfies:
 * <pre>
 *   predictionValue ≈ baselineValue + Σ shapValue.impact()
 * </pre>
 *
 * @param predictionValue  the model's output for the explained input
 * @param baselineValue    the model's expected output over the background data
 * @param shapValues       SHAP values ordered by {@code |impact|} descending
 *                         (length ≤ {@link io.gauss.core.annotation.Explainable#topFeatures()})
 */
public record ExplanationResult(
        double          predictionValue,
        double          baselineValue,
        List<ShapValue> shapValues
) {

    public ExplanationResult {
        shapValues = List.copyOf(shapValues);
    }

    /**
     * The sum of all included SHAP values.  For the top-K explanation this
     * approximates but may not exactly equal
     * {@code predictionValue - baselineValue}.
     */
    public double totalAttributed() {
        return shapValues.stream().mapToDouble(ShapValue::impact).sum();
    }

    /**
     * Returns the SHAP value for {@code featureName}, or {@code 0.0} if the
     * feature is not in the top-K list.
     */
    public double impactOf(String featureName) {
        return shapValues.stream()
                .filter(v -> v.featureName().equals(featureName))
                .mapToDouble(ShapValue::impact)
                .findFirst()
                .orElse(0.0);
    }
}
