package com.example.localdocumentassistant.documentcatalog;

public enum DocumentProcessingStatus {
    DISCOVERED,
    NEEDS_PROCESSING,
    CHUNKED,
    PROCESSING,
    INDEXED,
    NO_EXTRACTABLE_TEXT,
    FAILED,
    SKIPPED
}
