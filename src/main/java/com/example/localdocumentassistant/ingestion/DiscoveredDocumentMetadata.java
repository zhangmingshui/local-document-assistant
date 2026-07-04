package com.example.localdocumentassistant.ingestion;

import java.nio.file.Path;

public record DiscoveredDocumentMetadata(
        Path path,
        String filePath,
        String fileName,
        String fileType,
        long fileSize,
        String lastModifiedAt
) {
}
