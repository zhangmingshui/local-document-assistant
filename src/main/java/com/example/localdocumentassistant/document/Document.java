package com.example.localdocumentassistant.document;

public record Document(
        Long id,
        String documentUuid,
        Long watchedFolderId,
        String filePath,
        String fileName,
        String fileType,
        Long fileSize,
        String lastModifiedAt,
        String contentHash,
        DocumentProcessingStatus processingStatus,
        int chunkCount,
        String lastProcessedAt
) {
}
