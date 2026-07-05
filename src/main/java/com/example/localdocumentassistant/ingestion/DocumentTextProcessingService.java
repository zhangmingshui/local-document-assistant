package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;
import com.example.localdocumentassistant.indexing.DocumentIndexingService;

@Service
public class DocumentTextProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentTextProcessingService.class);

    private final DocumentRepository documentRepository;
    private final List<DocumentTextExtractor> extractors;
    private final TextChunker textChunker;
    private final DocumentIndexingService documentIndexingService;

    public DocumentTextProcessingService(
            DocumentRepository documentRepository,
            List<DocumentTextExtractor> extractors,
            TextChunker textChunker,
            DocumentIndexingService documentIndexingService
    ) {
        this.documentRepository = documentRepository;
        this.extractors = extractors;
        this.textChunker = textChunker;
        this.documentIndexingService = documentIndexingService;
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
            documentIndexingService.index(document, chunks);
            return DocumentProcessingOutcome.SUCCESSFUL;
        } catch (IOException | RuntimeException processingError) {
            LOGGER.warn("Could not extract, chunk, or index document path={}", document.filePath(), processingError);
            return DocumentProcessingOutcome.FAILED;
        }
    }
}
