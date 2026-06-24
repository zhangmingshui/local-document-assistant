package com.example.localdocumentassistant.processing;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository {

    Document create(Document document);

    Optional<Document> findByDocumentUuid(String documentUuid);

    List<Document> findByWatchedFolderId(Long watchedFolderId);

    Optional<Document> findByWatchedFolderIdAndFilePath(Long watchedFolderId, String filePath);

    Document update(Document document);

    Document updateProcessingStatus(Long id, DocumentProcessingStatus status);
}
