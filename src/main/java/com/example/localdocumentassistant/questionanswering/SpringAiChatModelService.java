package com.example.localdocumentassistant.questionanswering;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("spring-ai & !custom-ollama")
public class SpringAiChatModelService implements ChatModelService {

    private final ChatClient chatClient;

    public SpringAiChatModelService(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public String generateAnswer(String prompt) {
        try {
            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                throw new ChatModelUnavailableException("Spring AI returned no answer.");
            }
            return answer;
        } catch (RuntimeException error) {
            if (error instanceof ChatModelUnavailableException chatModelUnavailableException) {
                throw chatModelUnavailableException;
            }
            throw new ChatModelUnavailableException(
                    "Spring AI chat is unavailable. Check that Ollama is running and the configured chat model is installed.",
                    error
            );
        }
    }
}
