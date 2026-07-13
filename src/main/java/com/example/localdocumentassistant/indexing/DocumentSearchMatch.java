package com.example.localdocumentassistant.indexing;

public record DocumentSearchMatch(
        String text,
        String fileName,
        String filePath,
        int chunkIndex,
        Long documentId,
        String documentUuid,
        double distance
) {
    public double relevance() {
        return 1.0 / (1.0 + distance);
    }
}
