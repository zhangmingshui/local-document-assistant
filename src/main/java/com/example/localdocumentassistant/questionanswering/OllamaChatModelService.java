package com.example.localdocumentassistant.questionanswering;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class OllamaChatModelService implements ChatModelService {

    private final RestClient restClient;
    private final String chatModel;

    public OllamaChatModelService(
            RestClient.Builder restClientBuilder,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen3:8b}") String chatModel
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.chatModel = chatModel;
    }

    @Override
    public String generateAnswer(String prompt) {
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaChatRequest(
                            chatModel,
                            false,
                            List.of(new OllamaMessage("user", prompt))
                    ))
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null
                    || response.message().content() == null
                    || response.message().content().isBlank()) {
                throw new ChatModelUnavailableException("Ollama returned no answer.");
            }
            return response.message().content();
        } catch (RestClientException error) {
            throw new ChatModelUnavailableException(
                    "Ollama chat is unavailable. Check that Ollama is running and the configured chat model is installed.",
                    error
            );
        }
    }

    private record OllamaChatRequest(String model, boolean stream, List<OllamaMessage> messages) {
    }

    private record OllamaMessage(String role, String content) {
    }

    private record OllamaChatResponse(OllamaMessage message) {
    }
}
