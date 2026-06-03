package io.gauss.augur.guardrail;

import java.util.regex.Pattern;

/**
 * A named regex pattern used to detect prompt injection or prohibited content
 * in LLM inputs (Augur module, HU-052).
 *
 * <p>Built-in patterns cover common attack vectors.  Custom patterns can be
 * added via {@link PromptGuardrailService#withPattern(GuardrailPattern)}.
 *
 * @param name        short identifier shown in block notifications
 * @param regex       Java regex matched against the raw input string
 * @param description human-readable explanation of what this pattern blocks
 */
public record GuardrailPattern(String name, String regex, String description) {

    /** Compiled regex for efficient repeated matching. */
    public Pattern compiled() {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    }

    // -------------------------------------------------------------------------
    // Built-in patterns
    // -------------------------------------------------------------------------

    /** Detects classic "ignore previous instructions" prompt injection. */
    public static final GuardrailPattern IGNORE_INSTRUCTIONS = new GuardrailPattern(
            "ignore_instructions",
            "ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions?",
            "Detects 'ignore previous instructions' prompt injection");

    /** Detects attempts to override the system prompt. */
    public static final GuardrailPattern SYSTEM_OVERRIDE = new GuardrailPattern(
            "system_override",
            "(system\\s+prompt|\\[system\\]|###\\s*system).*?(override|replace|ignore|disregard)",
            "Detects attempts to override or replace the system prompt");

    /** Detects jailbreak role-play patterns (DAN, act as, pretend to be). */
    public static final GuardrailPattern JAILBREAK_ROLEPLAY = new GuardrailPattern(
            "jailbreak_roleplay",
            "(do\\s+anything\\s+now|act\\s+as\\s+(?!a\\s+customer)|pretend\\s+(you\\s+are|to\\s+be)\\s+(?!a\\s+(helpful|assistant)))",
            "Detects DAN-style jailbreak and role-play manipulation");

    /** Detects delimiter injection (hidden instructions via common delimiters). */
    public static final GuardrailPattern DELIMITER_INJECTION = new GuardrailPattern(
            "delimiter_injection",
            "(```|<\\|im_start\\|>|<\\|endoftext\\|>|\\[INST\\]|<<SYS>>)",
            "Detects hidden instruction injection via LLM delimiters");

    /** Detects requests for the model's own system prompt. */
    public static final GuardrailPattern PROMPT_EXFILTRATION = new GuardrailPattern(
            "prompt_exfiltration",
            "(repeat|print|show|reveal|output)\\s+(?:me\\s+)?(your|the)\\s+(system\\s+)?prompt",
            "Detects attempts to exfiltrate the system prompt");
}
