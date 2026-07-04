package com.example.localdocumentassistant.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.source.DocumentSource;

@Service
public class DocumentInventoryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentInventoryService.class);

    private final DocumentRepository documentRepository;

    public DocumentInventoryService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    public void persistDiscoveredDocuments(
            DocumentSource source,
            List<DiscoveredDocumentMetadata> discoveredDocuments
    ) {
        for (DiscoveredDocumentMetadata discoveredDocument : discoveredDocuments) {
            Optional<Document> existingDocument = documentRepository
                    .findByWatchedFolderIdAndFilePath(source.id(), discoveredDocument.filePath());

            if (existingDocument.filter(document -> metadataAndHashAreUnchanged(document, discoveredDocument))
                    .isPresent()) {
                LOGGER.info("Skipped content hash for unchanged document path={}", discoveredDocument.filePath());
                continue;
            }

            String contentHash = calculateSha256(discoveredDocument.path());
            Document document = existingDocument
                    .map(existing -> documentFromExisting(existing, discoveredDocument, contentHash))
                    .orElseGet(() -> newDocument(source, discoveredDocument, contentHash));

            Document savedDocument = document.id() == null
                    ? documentRepository.create(document)
                    : documentRepository.update(document);
            LOGGER.info("Saved discovered document metadata id={} uuid={} path={}",
                    savedDocument.id(), savedDocument.documentUuid(), savedDocument.filePath());
        }
    }

    private boolean metadataAndHashAreUnchanged(
            Document existingDocument,
            DiscoveredDocumentMetadata discoveredDocument
    ) {
        return existingDocument.contentHash() != null
                && Objects.equals(existingDocument.fileSize(), discoveredDocument.fileSize())
                && Objects.equals(existingDocument.lastModifiedAt(), discoveredDocument.lastModifiedAt());
    }

    private String calculateSha256(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailableAlgorithm) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", unavailableAlgorithm);
        }

        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        } catch (IOException readError) {
            throw new UncheckedIOException("Could not hash discovered document: " + file, readError);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private Document newDocument(
            DocumentSource source,
            DiscoveredDocumentMetadata discoveredDocument,
            String contentHash
    ) {
        return new Document(
                null,
                null,
                source.id(),
                discoveredDocument.filePath(),
                discoveredDocument.fileName(),
                discoveredDocument.fileType(),
                discoveredDocument.fileSize(),
                discoveredDocument.lastModifiedAt(),
                contentHash,
                DocumentProcessingStatus.NEEDS_PROCESSING,
                0,
                null
        );
    }

    private Document documentFromExisting(
            Document existingDocument,
            DiscoveredDocumentMetadata discoveredDocument,
            String contentHash
    ) {
        DocumentProcessingStatus processingStatus = existingDocument.contentHash() == null
                || !existingDocument.contentHash().equals(contentHash)
                ? DocumentProcessingStatus.NEEDS_PROCESSING
                : existingDocument.processingStatus();

        return new Document(
                existingDocument.id(),
                existingDocument.documentUuid(),
                existingDocument.watchedFolderId(),
                discoveredDocument.filePath(),
                discoveredDocument.fileName(),
                discoveredDocument.fileType(),
                discoveredDocument.fileSize(),
                discoveredDocument.lastModifiedAt(),
                contentHash,
                processingStatus,
                existingDocument.chunkCount(),
                existingDocument.lastProcessedAt()
        );
    }
}
