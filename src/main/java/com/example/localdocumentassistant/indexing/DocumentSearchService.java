package com.example.localdocumentassistant.indexing;

import java.util.List;

import org.springframework.stereotype.Service;

@Service
public class DocumentSearchService {

    private final EmbeddingService embeddingService;
    private final DocumentVectorStore documentVectorStore;

    public DocumentSearchService(
            EmbeddingService embeddingService,
            DocumentVectorStore documentVectorStore
    ) {
        this.embeddingService = embeddingService;
        this.documentVectorStore = documentVectorStore;
    }

    public List<DocumentSearchMatch> search(String query, int limit) {
        return documentVectorStore.search(embeddingService.embed(query), limit);
    }
}
