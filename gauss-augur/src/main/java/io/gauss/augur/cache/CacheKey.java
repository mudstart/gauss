package io.gauss.augur.cache;

import java.util.Objects;

/**
 * Composite cache key for prediction results: {@code (endpointName, inputHash)}.
 *
 * <p>The key is used by {@link PredictionCache} to store and retrieve predictions
 * transparently without the prediction method knowing about caching (HU-021).
 *
 * <pre>{@code
 * CacheKey key = CacheKey.of("churn", customerInput);
 * Optional<Object> cached = predictionCache.get(key);
 * }</pre>
 */
public record CacheKey(String endpointName, int inputHash) {

    /**
     * Creates a key from an endpoint name and a prediction input.
     * Uses {@link Objects#hashCode(Object)} to derive the input hash, so
     * input objects should implement {@link Object#hashCode()} meaningfully
     * (e.g., value-based equality).
     *
     * @param endpointName the {@code @MLEndpoint} class simple name
     * @param input        the prediction input object
     * @return a new {@code CacheKey}
     */
    public static CacheKey of(String endpointName, Object input) {
        return new CacheKey(endpointName, Objects.hashCode(input));
    }

    /**
     * Returns a string representation suitable for use as a Caffeine cache key.
     * Format: {@code "endpointName:inputHash"}.
     */
    public String asString() {
        return endpointName + ":" + inputHash;
    }
}
