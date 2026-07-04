package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;

@Service
public class DocumentTextProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTextProcessingService.class);

    private final DocumentRepository documentRepository;
    private final List<DocumentTextExtractor> extractors;
    private final TextChunker textChunker;

    public DocumentTextProcessingService(
            DocumentRepository documentRepository,
            List<DocumentTextExtractor> extractors,
            TextChunker textChunker
    ) {
        this.documentRepository = documentRepository;
        this.extractors = extractors;
        this.textChunker = textChunker;
    }

    public List<Document> findDocumentsNeedingProcessing(Long watchedFolderId) {
        return documentRepository.findByWatchedFolderId(watchedFolderId).stream()
                .filter(document -> document.processingStatus() == DocumentProcessingStatus.NEEDS_PROCESSING)
                .toList();
    }

    public DocumentProcessingOutcome processDocument(Document document) {
        Optional<DocumentTextExtractor> extractor = extractors.stream()
                .filter(candidate -> candidate.supports(document.fileType()))
                .findFirst();

        if (extractor.isEmpty()) {
            LOGGER.info("No text extractor is available for document type={} path={}",
                    document.fileType(), document.filePath());
            return DocumentProcessingOutcome.SKIPPED;
        }

        try {
            String text = extractor.orElseThrow().extract(Path.of(document.filePath()));
            List<TextChunk> chunks = textChunker.chunk(text);
            documentRepository.update(new Document(
                    document.id(),
                    document.documentUuid(),
                    document.watchedFolderId(),
                    document.filePath(),
                    document.fileName(),
                    document.fileType(),
                    document.fileSize(),
                    document.lastModifiedAt(),
                    document.contentHash(),
                    DocumentProcessingStatus.CHUNKED,
                    chunks.size(),
                    Instant.now().toString()
            ));
            return DocumentProcessingOutcome.SUCCESSFUL;
        } catch (IOException | RuntimeException processingError) {
            LOGGER.warn("Could not extract or chunk document path={}", document.filePath(), processingError);
            return DocumentProcessingOutcome.FAILED;
        }
    }
}
