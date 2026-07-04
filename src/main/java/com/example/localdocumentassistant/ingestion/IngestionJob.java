package com.example.localdocumentassistant.ingestion;

public record IngestionJob(
        Long id,
        String jobId,
        Long watchedFolderId,
        IngestionJobStatus status,
        int totalFiles,
        int processedFiles,
        int successfulFiles,
        int failedFiles,
        int skippedFiles,
        String currentFile,
        String currentStep,
        int currentChunk,
        int totalChunksForCurrentFile,
        String startedAt,
        String completedAt
) {
}
