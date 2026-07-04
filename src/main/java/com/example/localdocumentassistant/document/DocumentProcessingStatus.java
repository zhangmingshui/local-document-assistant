package com.example.localdocumentassistant.document;

public enum DocumentProcessingStatus {
    DISCOVERED,
    NEEDS_PROCESSING,
    CHUNKED,
    PROCESSING,
    INDEXED,
    FAILED,
    SKIPPED
}
