package io.gauss.augur.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link LLMProviderRegistry}.
 */
class LLMProviderRegistryTest {

    private LLMProviderRegistry registry;
    private ChatLanguageModel   mockModel;

    @BeforeEach
    void setUp() {
        registry  = new LLMProviderRegistry();
        mockModel = mock(ChatLanguageModel.class);
    }

    @Test
    void register_and_find_byName() {
        registry.register("openai", mockModel);
        assertThat(registry.find("openai")).contains(mockModel);
    }

    @Test
    void find_caseInsensitive() {
        registry.register("OpenAI", mockModel);
        assertThat(registry.find("openai")).contains(mockModel);
        assertThat(registry.find("OPENAI")).contains(mockModel);
    }

    @Test
    void find_returnsEmptyForUnknownProvider() {
        assertThat(registry.find("anthropic")).isEmpty();
    }

    @Test
    void require_throwsWhenNotRegistered() {
        assertThatIllegalStateException()
                .isThrownBy(() -> registry.require("ollama"))
                .withMessageContaining("No ChatLanguageModel registered")
                .withMessageContaining("ollama");
    }

    @Test
    void require_returnsModelWhenRegistered() {
        registry.register("ollama", mockModel);
        assertThat(registry.require("ollama")).isSameAs(mockModel);
    }

    @Test
    void isRegistered_reflectsRegistrationState() {
        assertThat(registry.isRegistered("openai")).isFalse();
        registry.register("openai", mockModel);
        assertThat(registry.isRegistered("openai")).isTrue();
    }

    @Test
    void register_rejectsNullProvider() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.register(null, mockModel));
    }

    @Test
    void register_rejectsNullModel() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> registry.register("openai", null));
    }
}
