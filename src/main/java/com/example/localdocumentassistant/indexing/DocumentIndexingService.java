package com.example.localdocumentassistant.indexing;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;
import com.example.localdocumentassistant.ingestion.TextChunk;

@Service
public class DocumentIndexingService {

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final DocumentVectorStore documentVectorStore;

    public DocumentIndexingService(
            DocumentRepository documentRepository,
            EmbeddingService embeddingService,
            DocumentVectorStore documentVectorStore
    ) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.documentVectorStore = documentVectorStore;
    }

    public void index(Document document, List<TextChunk> chunks) {
        List<EmbeddedDocumentChunk> embeddedChunks = chunks.stream()
                .map(chunk -> toEmbeddedChunk(document, chunk))
                .toList();

        documentVectorStore.deleteByDocumentUuid(document.documentUuid());
        documentVectorStore.store(embeddedChunks);
        documentRepository.update(indexedDocument(document, chunks.size()));
    }

    private EmbeddedDocumentChunk toEmbeddedChunk(Document document, TextChunk chunk) {
        return new EmbeddedDocumentChunk(
                "document-" + document.documentUuid() + "-chunk-" + chunk.chunkIndex(),
                chunk.text(),
                embeddingService.embed(chunk.text()),
                new DocumentChunkMetadata(
                        document.id(),
                        document.documentUuid(),
                        document.watchedFolderId(),
                        document.fileName(),
                        document.filePath(),
                        document.fileType(),
                        document.contentHash(),
                        chunk.chunkIndex()
                )
        );
    }

    private Document indexedDocument(Document document, int chunkCount) {
        return new Document(
                document.id(),
                document.documentUuid(),
                document.watchedFolderId(),
                document.filePath(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.lastModifiedAt(),
                document.contentHash(),
                DocumentProcessingStatus.INDEXED,
                chunkCount,
                Instant.now().toString()
        );
    }
}
