package io.gauss.augur.llm;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import io.gauss.core.annotation.LLMEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link LLMServiceFactory}.
 */
class LLMServiceFactoryTest {

    @LLMEndpoint(provider = "openai", model = "gpt-4o")
    interface GreetingService {
        String greet(String name);
    }

    @LLMEndpoint(provider = "ollama", model = "llama3")
    interface SummaryService {
        String summarize(String text);
    }

    interface Unannotated { String call(String s); }

    private LLMProviderRegistry  registry;
    private LLMServiceFactory    factory;
    private ChatLanguageModel    mockModel;

    @BeforeEach
    void setUp() {
        registry  = new LLMProviderRegistry();
        factory   = new LLMServiceFactory(registry);
        mockModel = mock(ChatLanguageModel.class);

        // AiServices calls the abstract generate(List<ChatMessage>) method
        //noinspection unchecked
        when(mockModel.generate(anyList()))
                .thenReturn(Response.from(AiMessage.from("Hello, World!")));
    }

    @Test
    void create_withExplicitModel_returnsProxy() {
        GreetingService service = factory.create(GreetingService.class, mockModel);
        assertThat(service).isNotNull();
        assertThat(service).isInstanceOf(GreetingService.class);
    }

    @Test
    void create_proxyInvokesModelOnCall() {
        GreetingService service = factory.create(GreetingService.class, mockModel);
        String result = service.greet("Alice");
        assertThat(result).isEqualTo("Hello, World!");
        verify(mockModel, atLeastOnce()).generate(anyList());
    }

    @Test
    void create_fromRegistry_usesRegisteredModel() {
        registry.register("openai", mockModel);
        GreetingService service = factory.create(GreetingService.class);
        assertThat(service).isNotNull();
    }

    @Test
    void create_throwsWhenProviderNotRegistered() {
        assertThatIllegalStateException()
                .isThrownBy(() -> factory.create(SummaryService.class))
                .withMessageContaining("ollama");
    }

    @Test
    void create_throwsForUnannotatedInterface() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(Unannotated.class, mockModel))
                .withMessageContaining("@LLMEndpoint");
    }
}
