package com.example.localdocumentassistant.questionanswering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

class ChatModelServiceProfileTest {

    @Test
    void customOllamaProfileWiresCustomOllamaChatModelService() {
        new ApplicationContextRunner()
                .withUserConfiguration(CustomOllamaProfileTestConfiguration.class)
                .withPropertyValues("spring.profiles.active=custom-ollama")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(ChatModelService.class)
                            .hasSingleBean(OllamaChatModelService.class)
                            .doesNotHaveBean(SpringAiChatModelService.class);
                    assertThat(ReflectionTestUtils.getField(
                            context.getBean(OllamaChatModelService.class),
                            "chatModel"
                    )).isEqualTo("qwen2.5:3b");
                });
    }

    @Test
    void springAiProfileWiresSpringAiChatModelService() {
        new ApplicationContextRunner()
                .withUserConfiguration(SpringAiProfileTestConfiguration.class)
                .withPropertyValues("spring.profiles.active=spring-ai")
                .run(context -> {
                    assertThat(context)
                            .hasSingleBean(ChatModelService.class)
                            .hasSingleBean(SpringAiChatModelService.class)
                            .doesNotHaveBean(OllamaChatModelService.class);
                    assertThat(ReflectionTestUtils.getField(
                            context.getBean(SpringAiChatModelService.class),
                            "chatModel"
                    )).isEqualTo("qwen2.5:3b");
                });
    }

    @Configuration
    @Import({OllamaChatModelService.class, SpringAiChatModelService.class})
    static class CustomOllamaProfileTestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }
    }

    @Configuration
    @Import({OllamaChatModelService.class, SpringAiChatModelService.class})
    static class SpringAiProfileTestConfiguration {

        @Bean
        ChatClient.Builder chatClientBuilder() {
            ChatClient.Builder builder = mock(ChatClient.Builder.class);
            when(builder.build()).thenReturn(mock(ChatClient.class));
            return builder;
        }
    }
}
