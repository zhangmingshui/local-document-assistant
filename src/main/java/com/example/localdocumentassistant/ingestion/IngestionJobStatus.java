package com.example.localdocumentassistant.ingestion;

public enum IngestionJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    COMPLETED_WITH_ERRORS,
    FAILED,
    CANCELLED
}
