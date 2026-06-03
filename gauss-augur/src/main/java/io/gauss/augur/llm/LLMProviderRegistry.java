package io.gauss.augur.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry that maps LLM provider names to {@link ChatLanguageModel} instances.
 *
 * <p>Users configure providers before any {@code @LLMEndpoint} service is resolved:
 * <pre>{@code
 * LLMProviderRegistry registry = new LLMProviderRegistry();
 * registry.register("openai", OpenAiChatModel.builder()
 *         .apiKey(System.getenv("OPENAI_KEY"))
 *         .modelName("gpt-4o")
 *         .build());
 * registry.register("ollama", OllamaChatModel.builder()
 *         .baseUrl("http://localhost:11434")
 *         .modelName("llama3")
 *         .build());
 * }</pre>
 *
 * <p>The registry is intentionally decoupled from specific providers so that
 * {@code gauss-augur} only depends on {@code langchain4j-core}, not any
 * provider-specific JAR.
 */
public class LLMProviderRegistry {

    private final Map<String, ChatLanguageModel> models = new ConcurrentHashMap<>();

    /**
     * Registers a {@link ChatLanguageModel} under the given provider name.
     * If a model is already registered for that name it is replaced.
     *
     * @param providerName lowercase provider name (e.g. {@code "openai"}, {@code "ollama"})
     * @param model        fully configured language model
     */
    public void register(String providerName, ChatLanguageModel model) {
        if (providerName == null || providerName.isBlank()) {
            throw new IllegalArgumentException("providerName must not be null or blank");
        }
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        models.put(providerName.toLowerCase(), model);
    }

    /**
     * Returns the model registered for {@code providerName}, or empty if none.
     */
    public Optional<ChatLanguageModel> find(String providerName) {
        return Optional.ofNullable(models.get(
                providerName == null ? null : providerName.toLowerCase()));
    }

    /**
     * Returns the model registered for {@code providerName}.
     *
     * @throws IllegalStateException if no model is registered for the provider
     */
    public ChatLanguageModel require(String providerName) {
        return find(providerName).orElseThrow(() -> new IllegalStateException(
                "No ChatLanguageModel registered for provider '" + providerName +
                "'. Call LLMProviderRegistry.register() before using this endpoint."));
    }

    /** Returns {@code true} if a model is registered for {@code providerName}. */
    public boolean isRegistered(String providerName) {
        return find(providerName).isPresent();
    }
}
