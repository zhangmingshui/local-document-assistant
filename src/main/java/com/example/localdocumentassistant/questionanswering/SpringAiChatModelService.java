package com.example.localdocumentassistant.questionanswering;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("spring-ai & !custom-ollama")
public class SpringAiChatModelService implements ChatModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SpringAiChatModelService.class);

    private final ChatClient chatClient;
    private final String chatModel;

    public SpringAiChatModelService(
            ChatClient.Builder chatClientBuilder,
            @Value("${ollama.chat-model:qwen2.5:3b}") String chatModel
    ) {
        this.chatClient = chatClientBuilder.build();
        this.chatModel = chatModel;
    }

    @Override
    public String generateAnswer(String prompt) {
        long startNanos = System.nanoTime();
        try {
            String answer = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (answer == null || answer.isBlank()) {
                logChatMetrics(prompt, null, startNanos);
                throw new ChatModelUnavailableException("Spring AI returned no answer.");
            }
            logChatMetrics(prompt, answer, startNanos);
            return answer;
        } catch (RuntimeException error) {
            if (error instanceof ChatModelUnavailableException chatModelUnavailableException) {
                throw chatModelUnavailableException;
            }
            logChatMetrics(prompt, null, startNanos);
            throw new ChatModelUnavailableException(
                    "Spring AI chat is unavailable. Check that Ollama is running and the configured chat model is installed.",
                    error
            );
        }
    }

    private void logChatMetrics(String prompt, String answer, long startNanos) {
        LOGGER.info(
                "CHAT_MODEL_METRICS provider=spring-ai model={} promptChars={} elapsedMs={} answerChars={}",
                chatModel,
                prompt == null ? 0 : prompt.length(),
                (System.nanoTime() - startNanos) / 1_000_000,
                answer == null ? 0 : answer.length()
        );
    }
}
