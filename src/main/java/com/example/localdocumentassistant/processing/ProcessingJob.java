package com.example.localdocumentassistant.processing;

public record ProcessingJob(
        Long id,
        String jobId,
        Long watchedFolderId,
        ProcessingJobStatus status,
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
