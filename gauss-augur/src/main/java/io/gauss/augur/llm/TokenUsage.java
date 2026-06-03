package io.gauss.augur.llm;

import java.time.Instant;

/**
 * Immutable snapshot of token consumption for a single LLM call (HU-058).
 *
 * <p>Recorded by {@link TokenUsageTracker} after each {@code @LLMEndpoint}
 * invocation and exposed as Micrometer metrics under the
 * {@code dsml.llm.tokens} key.
 *
 * @param inputTokens   number of prompt / input tokens consumed
 * @param outputTokens  number of completion / output tokens generated
 * @param provider      LLM provider identifier (e.g., {@code "openai"}, {@code "ollama"})
 * @param model         model name (e.g., {@code "gpt-4o"}, {@code "llama3"})
 * @param endpointName  the {@code @LLMEndpoint} class name that triggered the call
 * @param timestamp     wall-clock time of the call
 */
public record TokenUsage(
        long    inputTokens,
        long    outputTokens,
        String  provider,
        String  model,
        String  endpointName,
        Instant timestamp
) {

    /** Total tokens (input + output). */
    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    /** Factory for the common case where the timestamp is {@link Instant#now()}. */
    public static TokenUsage of(long inputTokens, long outputTokens,
                                 String provider, String model, String endpointName) {
        return new TokenUsage(inputTokens, outputTokens, provider, model,
                              endpointName, Instant.now());
    }
}
