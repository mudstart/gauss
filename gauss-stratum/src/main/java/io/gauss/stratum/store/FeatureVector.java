package io.gauss.stratum.store;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * A snapshot of all feature values for a single entity (HU-028 / HU-029).
 *
 * <p>The vector is the output of {@link FeatureStore#getAll(String, Object, Class)}
 * and can be injected directly into a {@code @DataPipeline} as a typed dataset.
 *
 * @param entityId   the entity for which features were computed
 * @param values     map from feature name to computed value
 * @param computedAt wall-clock time when the vector was assembled
 */
public record FeatureVector(
        String              entityId,
        Map<String, Object> values,
        Instant             computedAt
) {

    public static FeatureVector of(String entityId, Map<String, Object> values) {
        return new FeatureVector(entityId, Map.copyOf(values), Instant.now());
    }

    /**
     * Returns the value for the named feature, or empty if it was not computed.
     */
    public Optional<Object> get(String featureName) {
        return Optional.ofNullable(values.get(featureName));
    }

    /**
     * Returns the value cast to {@code T}, or empty.
     *
     * @throws ClassCastException if the stored value is not assignable to {@code T}
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> get(String featureName, Class<T> type) {
        Object value = values.get(featureName);
        if (value == null) return Optional.empty();
        return Optional.of(type.cast(value));
    }

    /** Returns the number of features in this vector. */
    public int size() { return values.size(); }
}
