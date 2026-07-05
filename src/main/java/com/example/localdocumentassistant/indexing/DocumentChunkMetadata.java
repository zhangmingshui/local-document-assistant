package com.example.localdocumentassistant.indexing;

public record DocumentChunkMetadata(
        Long documentId,
        String documentUuid,
        Long watchedFolderId,
        String fileName,
        String filePath,
        String fileType,
        String contentHash,
        int chunkIndex
) {
}
