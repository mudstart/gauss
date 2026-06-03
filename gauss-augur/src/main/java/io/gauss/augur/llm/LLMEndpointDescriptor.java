package io.gauss.augur.llm;

import io.gauss.core.annotation.LLMEndpoint;

/**
 * Immutable metadata extracted from a {@link LLMEndpoint @LLMEndpoint}-annotated
 * interface at startup.
 *
 * @param serviceInterface the annotated interface
 * @param provider         LLM provider name (e.g. {@code "openai"}, {@code "ollama"})
 * @param model            model identifier (e.g. {@code "gpt-4o"})
 * @param path             HTTP base path for the generated endpoint
 * @param guardrails       whether prompt guardrails are enabled
 */
public record LLMEndpointDescriptor(
        Class<?>  serviceInterface,
        String    provider,
        String    model,
        String    path,
        boolean   guardrails
) {

    /**
     * Builds a descriptor from the annotation on {@code serviceInterface}.
     *
     * @throws IllegalArgumentException if the class is not annotated with {@code @LLMEndpoint}
     */
    public static LLMEndpointDescriptor from(Class<?> serviceInterface) {
        LLMEndpoint ann = serviceInterface.getAnnotation(LLMEndpoint.class);
        if (ann == null) {
            throw new IllegalArgumentException(
                    serviceInterface.getName() + " is not annotated with @LLMEndpoint");
        }
        String path = ann.path().isBlank()
                ? "/api/llm/" + serviceInterface.getSimpleName()
                : ann.path();
        return new LLMEndpointDescriptor(
                serviceInterface,
                ann.provider(),
                ann.model(),
                path,
                ann.guardrails()
        );
    }
}
