package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
    private final Duration extractionTimeout;
    private final ExecutorService extractionExecutor = Executors.newCachedThreadPool();

    public DocumentTextProcessingService(
            DocumentRepository documentRepository,
            List<DocumentTextExtractor> extractors,
            TextChunker textChunker,
            DocumentIndexingService documentIndexingService,
            @Value("${ingestion.text-extraction.timeout:30s}") Duration extractionTimeout
    ) {
        this.documentRepository = documentRepository;
        this.extractors = extractors;
        this.textChunker = textChunker;
        this.documentIndexingService = documentIndexingService;
        this.extractionTimeout = extractionTimeout;
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
            Path documentPath = Path.of(document.filePath());
            if (Files.size(documentPath) == 0) {
                LOGGER.info("Document has no bytes to extract path={}", document.filePath());
                markNoExtractableText(document);
                return DocumentProcessingOutcome.SKIPPED;
            }

            String text = extractWithTimeout(extractor.orElseThrow(), documentPath);
            if (text == null || text.isBlank()) {
                LOGGER.info("Document has no extractable text path={}", document.filePath());
                markNoExtractableText(document);
                return DocumentProcessingOutcome.SKIPPED;
            }

            List<TextChunk> chunks = textChunker.chunk(text);
            if (chunks.isEmpty()) {
                LOGGER.info("Document text produced no chunks path={}", document.filePath());
                markNoExtractableText(document);
                return DocumentProcessingOutcome.SKIPPED;
            }

            documentIndexingService.index(document, chunks);
            return DocumentProcessingOutcome.SUCCESSFUL;
        } catch (IOException | RuntimeException processingError) {
            LOGGER.warn("Could not extract, chunk, or index document path={}", document.filePath(), processingError);
            return DocumentProcessingOutcome.FAILED;
        }
    }

    @PreDestroy
    public void shutdown() {
        extractionExecutor.shutdownNow();
    }

    private String extractWithTimeout(DocumentTextExtractor extractor, Path documentPath) throws IOException {
        Future<String> extraction = extractionExecutor.submit(extractionTask(extractor, documentPath));
        try {
            return extraction.get(extractionTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException timeout) {
            extraction.cancel(true);
            throw new IOException("Text extraction timed out for document: " + documentPath, timeout);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IOException("Text extraction was interrupted for document: " + documentPath, interrupted);
        } catch (ExecutionException executionError) {
            Throwable cause = executionError.getCause();
            if (cause instanceof IOException ioException) {
                throw ioException;
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IOException("Text extraction failed for document: " + documentPath, cause);
        }
    }

    private Callable<String> extractionTask(DocumentTextExtractor extractor, Path documentPath) {
        return () -> extractor.extract(documentPath);
    }

    private void markNoExtractableText(Document document) {
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
                DocumentProcessingStatus.NO_EXTRACTABLE_TEXT,
                0,
                Instant.now().toString()
        ));
    }
}
