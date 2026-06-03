package io.gauss.augur.guardrail;

import java.util.List;

/**
 * Thrown by {@link PromptGuardrailService} when an LLM input is rejected by
 * one or more guardrail patterns (HU-052).
 *
 * <p>The interceptor layer catches this exception, logs an audit event, and
 * returns a safe error response to the caller instead of forwarding the input
 * to the LLM provider.
 */
public class LLMGuardrailViolationException extends RuntimeException {

    private final List<GuardrailPattern> matchedPatterns;

    public LLMGuardrailViolationException(List<GuardrailPattern> matched) {
        super(buildMessage(matched));
        this.matchedPatterns = List.copyOf(matched);
    }

    /** Patterns whose regex matched the rejected input. */
    public List<GuardrailPattern> matchedPatterns() {
        return matchedPatterns;
    }

    private static String buildMessage(List<GuardrailPattern> matched) {
        String names = matched.stream()
                .map(GuardrailPattern::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("unknown");
        return "LLM input blocked by guardrail(s): " + names;
    }
}
