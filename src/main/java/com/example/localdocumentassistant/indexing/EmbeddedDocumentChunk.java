package com.example.localdocumentassistant.indexing;

import java.util.List;

public record EmbeddedDocumentChunk(
        String id,
        String text,
        List<Double> embedding,
        DocumentChunkMetadata metadata
) {
}
