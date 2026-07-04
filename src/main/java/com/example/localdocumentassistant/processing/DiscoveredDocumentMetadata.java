package com.example.localdocumentassistant.processing;

import java.nio.file.Path;

record DiscoveredDocumentMetadata(
        Path path,
        String filePath,
        String fileName,
        String fileType,
        long fileSize,
        String lastModifiedAt
) {
}
