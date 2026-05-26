package io.gauss.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Gauss LLM endpoint backed by LangChain4j.
 *
 * <p>The framework auto-configures the LangChain4j AI service for the
 * declared {@link #provider()} and {@link #model()}, wires up streaming
 * (methods returning {@code Multi<String>} map to {@code AsyncIterable<string>}
 * in TypeScript), and manages conversation history.
 *
 * <pre>{@code
 * @LLMEndpoint(provider = "openai", model = "gpt-4o")
 * public interface ChatService {
 *     String chat(@MemoryId String sessionId, @UserMessage String message);
 *     Multi<String> stream(@UserMessage String message);
 * }
 *
 * @LLMEndpoint(provider = "ollama", model = "llama3")
 * public interface LocalSummarizer {
 *     String summarize(String text);
 * }
 * }</pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface LLMEndpoint {

    /** LLM provider: {@code "openai"}, {@code "anthropic"}, {@code "ollama"}, {@code "azure-openai"}. */
    String provider();

    /** Model identifier as understood by the provider (e.g. {@code "gpt-4o"}, {@code "llama3"}). */
    String model() default "";

    /**
     * Enables prompt injection and jailbreak guardrails on all inputs.
     * When {@code true}, Gauss validates each user message before forwarding to the LLM.
     */
    boolean guardrails() default false;

    /** Base path for the generated HTTP endpoint. Defaults to "/api/llm/{className}". */
    String path() default "";
}
