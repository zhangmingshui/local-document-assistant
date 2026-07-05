package com.example.localdocumentassistant.indexing;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private final RestClient restClient;
    private final String embeddingModel;

    public OllamaEmbeddingService(
            RestClient.Builder restClientBuilder,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.embedding-model:nomic-embed-text}") String embeddingModel
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Double> embed(String text) {
        OllamaEmbedResponse response = restClient.post()
                .uri("/api/embed")
                .body(new OllamaEmbedRequest(embeddingModel, text))
                .retrieve()
                .body(OllamaEmbedResponse.class);

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new IllegalStateException("Ollama returned no embedding.");
        }
        return response.embeddings().get(0);
    }

    private record OllamaEmbedRequest(String model, String input) {
    }

    private record OllamaEmbedResponse(List<List<Double>> embeddings) {
    }
}
