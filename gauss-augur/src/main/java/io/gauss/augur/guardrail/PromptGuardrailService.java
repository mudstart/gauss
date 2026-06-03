package io.gauss.augur.guardrail;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates and sanitises LLM inputs against a configurable set of
 * {@link GuardrailPattern}s (Augur module, HU-052).
 *
 * <p>The service ships with five built-in patterns covering the most common
 * prompt injection and jailbreak techniques.  Teams can extend it via
 * {@link #withPattern(GuardrailPattern)} to register additional organisation-
 * specific rules (the SPI contract from the acceptance criteria).
 *
 * <p>Usage:
 * <pre>{@code
 * PromptGuardrailService guardrails = new PromptGuardrailService()
 *         .withPattern(new GuardrailPattern("no_pii", "\\d{3}-\\d{2}-\\d{4}", "Block SSNs"));
 *
 * // In an @LLMEndpoint interceptor:
 * guardrails.validate(userInput);   // throws LLMGuardrailViolationException if blocked
 * }</pre>
 */
public final class PromptGuardrailService {

    private final List<GuardrailPattern> patterns;

    /** Creates a service pre-loaded with all built-in patterns. */
    public PromptGuardrailService() {
        this(List.of(
                GuardrailPattern.IGNORE_INSTRUCTIONS,
                GuardrailPattern.SYSTEM_OVERRIDE,
                GuardrailPattern.JAILBREAK_ROLEPLAY,
                GuardrailPattern.DELIMITER_INJECTION,
                GuardrailPattern.PROMPT_EXFILTRATION
        ));
    }

    /** Creates a service with an explicit pattern list (replaces built-ins). */
    public PromptGuardrailService(List<GuardrailPattern> patterns) {
        this.patterns = List.copyOf(patterns);
    }

    // -------------------------------------------------------------------------
    // Validation API
    // -------------------------------------------------------------------------

    /**
     * Validates {@code input} against all registered patterns.
     *
     * @param input the raw user prompt to validate
     * @throws LLMGuardrailViolationException if one or more patterns match
     */
    public void validate(String input) {
        List<GuardrailPattern> matched = scan(input);
        if (!matched.isEmpty()) {
            throw new LLMGuardrailViolationException(matched);
        }
    }

    /**
     * Scans {@code input} and returns all matching patterns without throwing.
     *
     * @param input the string to scan
     * @return list of matched patterns (empty if input is safe)
     */
    public List<GuardrailPattern> scan(String input) {
        if (input == null || input.isBlank()) return List.of();
        List<GuardrailPattern> matched = new ArrayList<>();
        for (GuardrailPattern pattern : patterns) {
            if (pattern.compiled().matcher(input).find()) {
                matched.add(pattern);
            }
        }
        return List.copyOf(matched);
    }

    /**
     * Returns {@code true} if the input matches at least one guardrail pattern.
     */
    public boolean isBlocked(String input) {
        return !scan(input).isEmpty();
    }

    // -------------------------------------------------------------------------
    // SPI — pattern registration
    // -------------------------------------------------------------------------

    /**
     * Returns a new {@code PromptGuardrailService} that includes all current
     * patterns plus {@code newPattern}.
     *
     * @param newPattern the additional pattern to register
     * @return a new immutable service instance
     */
    public PromptGuardrailService withPattern(GuardrailPattern newPattern) {
        List<GuardrailPattern> extended = new ArrayList<>(patterns);
        extended.add(newPattern);
        return new PromptGuardrailService(extended);
    }

    /** Returns the number of registered patterns. */
    public int patternCount() {
        return patterns.size();
    }

    /** Returns an unmodifiable view of all registered patterns. */
    public List<GuardrailPattern> patterns() {
        return patterns;
    }
}
