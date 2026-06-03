package io.gauss.augur.llm;

import io.gauss.core.annotation.LLMEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link LLMEndpointDescriptor}.
 */
class LLMEndpointDescriptorTest {

    @LLMEndpoint(provider = "openai", model = "gpt-4o")
    interface ChatService {
        String chat(String message);
    }

    @LLMEndpoint(provider = "ollama", model = "llama3",
                 guardrails = true, path = "/api/chat/local")
    interface LocalChat {
        String ask(String question);
    }

    /** Not annotated. */
    interface Unannotated {
        String call(String input);
    }

    // -------------------------------------------------------------------------

    @Test
    void from_extractsProviderAndModel() {
        LLMEndpointDescriptor desc = LLMEndpointDescriptor.from(ChatService.class);
        assertThat(desc.provider()).isEqualTo("openai");
        assertThat(desc.model()).isEqualTo("gpt-4o");
    }

    @Test
    void from_defaultPathUsesSimpleClassName() {
        LLMEndpointDescriptor desc = LLMEndpointDescriptor.from(ChatService.class);
        assertThat(desc.path()).isEqualTo("/api/llm/ChatService");
    }

    @Test
    void from_customPathOverridesDefault() {
        LLMEndpointDescriptor desc = LLMEndpointDescriptor.from(LocalChat.class);
        assertThat(desc.path()).isEqualTo("/api/chat/local");
    }

    @Test
    void from_guardrailsFlagIsPreserved() {
        assertThat(LLMEndpointDescriptor.from(ChatService.class).guardrails()).isFalse();
        assertThat(LLMEndpointDescriptor.from(LocalChat.class).guardrails()).isTrue();
    }

    @Test
    void from_throwsForUnannotatedInterface() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> LLMEndpointDescriptor.from(Unannotated.class))
                .withMessageContaining("@LLMEndpoint");
    }

    @Test
    void from_storesServiceInterface() {
        LLMEndpointDescriptor desc = LLMEndpointDescriptor.from(ChatService.class);
        assertThat(desc.serviceInterface()).isEqualTo(ChatService.class);
    }
}
