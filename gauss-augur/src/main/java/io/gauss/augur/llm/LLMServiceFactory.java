package io.gauss.augur.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.service.AiServices;

/**
 * Creates type-safe LangChain4j AI service proxies for
 * {@link io.gauss.core.annotation.LLMEndpoint @LLMEndpoint}-annotated interfaces.
 *
 * <p>Usage:
 * <pre>{@code
 * LLMProviderRegistry registry = new LLMProviderRegistry();
 * registry.register("openai", openAiModel);
 *
 * LLMServiceFactory factory = new LLMServiceFactory(registry);
 * ChatService service = factory.create(ChatService.class);
 * String reply = service.chat("session-1", "Hello!");
 * }</pre>
 */
public class LLMServiceFactory {

    private final LLMProviderRegistry providerRegistry;

    public LLMServiceFactory(LLMProviderRegistry providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    /**
     * Creates a LangChain4j AI service proxy for the given {@code @LLMEndpoint} interface.
     *
     * @param serviceInterface interface annotated with {@code @LLMEndpoint}
     * @param <T>              the service type
     * @return ready-to-use proxy backed by the configured {@link ChatLanguageModel}
     * @throws IllegalArgumentException if the interface lacks {@code @LLMEndpoint}
     * @throws IllegalStateException    if the required provider is not registered
     */
    public <T> T create(Class<T> serviceInterface) {
        LLMEndpointDescriptor descriptor = LLMEndpointDescriptor.from(serviceInterface);
        ChatLanguageModel model = providerRegistry.require(descriptor.provider());
        return buildProxy(serviceInterface, model);
    }

    /**
     * Creates a service proxy using an explicitly supplied {@link ChatLanguageModel},
     * bypassing the provider registry.  Useful for tests.
     *
     * @param serviceInterface interface annotated with {@code @LLMEndpoint}
     * @param model            language model to back the service
     */
    public <T> T create(Class<T> serviceInterface, ChatLanguageModel model) {
        LLMEndpointDescriptor.from(serviceInterface);  // validates the annotation
        return buildProxy(serviceInterface, model);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static <T> T buildProxy(Class<T> serviceInterface, ChatLanguageModel model) {
        return AiServices.builder(serviceInterface)
                .chatLanguageModel(model)
                .build();
    }
}
