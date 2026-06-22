package com.example.localdocumentassistant.processing;

public record ProcessingJob(
        Long id,
        String jobId,
        Long watchedFolderId,
        String status,
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
