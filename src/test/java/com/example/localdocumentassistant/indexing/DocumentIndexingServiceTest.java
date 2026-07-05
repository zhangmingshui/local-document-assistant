package com.example.localdocumentassistant.indexing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;
import com.example.localdocumentassistant.ingestion.TextChunk;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private DocumentVectorStore documentVectorStore;

    private DocumentIndexingService documentIndexingService;

    @BeforeEach
    void setUp() {
        documentIndexingService = new DocumentIndexingService(
                documentRepository,
                embeddingService,
                documentVectorStore
        );
    }

    @Test
    void indexesChunksAndMarksDocumentIndexed() {
        Document document = needsProcessingDocument();
        List<TextChunk> chunks = List.of(
                new TextChunk(0, "first chunk"),
                new TextChunk(1, "second chunk")
        );
        when(embeddingService.embed("first chunk")).thenReturn(List.of(0.1, 0.2));
        when(embeddingService.embed("second chunk")).thenReturn(List.of(0.3, 0.4));

        documentIndexingService.index(document, chunks);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EmbeddedDocumentChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(documentVectorStore).store(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(2);
        assertThat(chunksCaptor.getValue().get(0).id()).isEqualTo("document-doc-uuid-1-chunk-0");
        assertThat(chunksCaptor.getValue().get(0).text()).isEqualTo("first chunk");
        assertThat(chunksCaptor.getValue().get(0).embedding()).containsExactly(0.1, 0.2);
        assertThat(chunksCaptor.getValue().get(0).metadata())
                .extracting(
                        DocumentChunkMetadata::documentId,
                        DocumentChunkMetadata::documentUuid,
                        DocumentChunkMetadata::watchedFolderId,
                        DocumentChunkMetadata::fileName,
                        DocumentChunkMetadata::filePath,
                        DocumentChunkMetadata::fileType,
                        DocumentChunkMetadata::contentHash,
                        DocumentChunkMetadata::chunkIndex
                )
                .containsExactly(12L, "doc-uuid-1", 7L, "notes.txt", "/tmp/notes.txt", "txt", "hash-1", 0);

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).update(documentCaptor.capture());
        assertThat(documentCaptor.getValue().processingStatus()).isEqualTo(DocumentProcessingStatus.INDEXED);
        assertThat(documentCaptor.getValue().chunkCount()).isEqualTo(2);
        assertThat(documentCaptor.getValue().lastProcessedAt()).isNotBlank();
    }

    @Test
    void removesOldChunksBeforeStoringReplacementChunks() {
        Document document = needsProcessingDocument();
        when(embeddingService.embed("replacement")).thenReturn(List.of(0.5, 0.6));

        documentIndexingService.index(document, List.of(new TextChunk(0, "replacement")));

        InOrder inOrder = inOrder(documentVectorStore);
        inOrder.verify(documentVectorStore).deleteByDocumentUuid(document.documentUuid());
        inOrder.verify(documentVectorStore).store(org.mockito.ArgumentMatchers.anyList());
    }

    private Document needsProcessingDocument() {
        return new Document(
                12L,
                "doc-uuid-1",
                7L,
                "/tmp/notes.txt",
                "notes.txt",
                "txt",
                100L,
                "2026-07-05T09:00:00Z",
                "hash-1",
                DocumentProcessingStatus.NEEDS_PROCESSING,
                0,
                null
        );
    }
}
