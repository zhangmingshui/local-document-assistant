package com.example.localdocumentassistant.indexing;

public record DocumentSearchMatch(
        String text,
        String fileName,
        String filePath,
        int chunkIndex,
        Long documentId,
        String documentUuid
) {
}
