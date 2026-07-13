package com.example.localdocumentassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DocumentSearchServiceTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentVectorStore documentVectorStore;

    private DocumentSearchService documentSearchService;

    @BeforeEach
    void setUp() {
        documentSearchService = new DocumentSearchService(embeddingService, documentVectorStore);
    }

    @Test
    void embedsQueryAndReturnsVectorStoreMatches() {
        List<Double> queryEmbedding = List.of(0.1, 0.2);
        DocumentSearchMatch match = new DocumentSearchMatch(
                "matching text",
                "notes.txt",
                "/tmp/notes.txt",
                0,
                12L,
                "doc-uuid-1",
                0.25
        );
        when(embeddingService.embed("search text")).thenReturn(queryEmbedding);
        when(documentVectorStore.search(queryEmbedding, 5)).thenReturn(List.of(match));

        List<DocumentSearchMatch> results = documentSearchService.search("search text", 5);

        assertThat(results).containsExactly(match);
        verify(embeddingService).embed("search text");
        verify(documentVectorStore).search(queryEmbedding, 5);
    }
}
