package com.example.localdocumentassistant.documentcatalog;

public enum DocumentProcessingStatus {
    DISCOVERED,
    NEEDS_PROCESSING,
    CHUNKED,
    PROCESSING,
    INDEXED,
    FAILED,
    SKIPPED
}
