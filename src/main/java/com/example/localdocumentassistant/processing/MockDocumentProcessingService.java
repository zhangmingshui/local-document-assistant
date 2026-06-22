package com.example.localdocumentassistant.processing;

import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class MockDocumentProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockDocumentProcessingService.class);

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ProcessingJobRepository processingJobRepository;

    public MockDocumentProcessingService(ProcessingJobRepository processingJobRepository) {
        this.processingJobRepository = processingJobRepository;
    }

    public void startMockProcessing(String jobId) {
        executorService.submit(() -> runMockProcessing(jobId));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runMockProcessing(String jobId) {
        int[] checkpoints = {5, 10, 15, 20};

        for (int processedFiles : checkpoints) {
            if (!sleepBetweenMockUpdates()) {
                return;
            }

            ProcessingJob currentJob = processingJobRepository.findByJobId(jobId).orElse(null);
            if (currentJob == null) {
                LOGGER.warn("Stopping mocked processing because job {} was not found.", jobId);
                return;
            }

            processingJobRepository.update(nextMockState(currentJob, processedFiles));
        }
    }

    private ProcessingJob nextMockState(ProcessingJob currentJob, int processedFiles) {
        boolean complete = processedFiles >= currentJob.totalFiles();
        int failedFiles = complete ? 1 : 0;
        int skippedFiles = complete ? 1 : 0;
        int successfulFiles = Math.max(processedFiles - failedFiles - skippedFiles, 0);

        return new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                complete ? ProcessingJobStatus.COMPLETED_WITH_ERRORS : ProcessingJobStatus.RUNNING,
                currentJob.totalFiles(),
                processedFiles,
                successfulFiles,
                failedFiles,
                skippedFiles,
                currentJob.currentFile(),
                currentStepFor(processedFiles, currentJob.totalFiles()),
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                complete ? Instant.now().toString() : null
        );
    }

    private String currentStepFor(int processedFiles, int totalFiles) {
        if (processedFiles >= totalFiles) {
            return "Mock run completed with one failed file and one skipped file";
        }

        if (processedFiles <= 5) {
            return "Preparing mocked file list";
        }

        return "Updating mocked processing counters";
    }

    private boolean sleepBetweenMockUpdates() {
        try {
            Thread.sleep(1500);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
