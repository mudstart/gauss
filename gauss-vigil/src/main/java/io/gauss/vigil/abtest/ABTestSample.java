package io.gauss.vigil.abtest;

/**
 * A single observation in an A/B test comparing two model versions (HU-056).
 *
 * <p>Each sample records which version served the request and the metric value
 * observed (e.g., binary success/failure, latency, accuracy).
 *
 * @param version  version identifier ({@code "A"} or {@code "B"}, or the model
 *                 version string such as {@code "v1"} / {@code "v2"})
 * @param value    numeric metric value; for binary outcomes use {@code 1.0} =
 *                 success and {@code 0.0} = failure
 */
public record ABTestSample(String version, double value) {

    /** Convenience factory for a binary success observation. */
    public static ABTestSample success(String version) {
        return new ABTestSample(version, 1.0);
    }

    /** Convenience factory for a binary failure observation. */
    public static ABTestSample failure(String version) {
        return new ABTestSample(version, 0.0);
    }
}
