package com.example.localdocumentassistant.processing;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.api.MockApiController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobResponse;

@Service
public class ProcessingJobService {

    private final Map<String, MockProcessingJob> processingJobs = new ConcurrentHashMap<>();
    private final AtomicInteger jobSequence = new AtomicInteger();

    public StartProcessingJobResponse startProcessingJob(StartProcessingJobRequest request) {
        String jobId = "mock-job-%03d".formatted(jobSequence.incrementAndGet());
        processingJobs.put(jobId, new MockProcessingJob(jobId, Instant.now()));

        return new StartProcessingJobResponse(
                jobId,
                "QUEUED",
                "Mock processing job started. No files are being scanned yet.",
                "/api/processing-jobs/" + jobId
        );
    }

    public Optional<ProcessingJobResponse> advanceProcessingJob(String jobId) {
        MockProcessingJob job = processingJobs.get(jobId);

        if (job == null) {
            return Optional.empty();
        }

        return Optional.of(job.advanceAndSnapshot());
    }

    private static class MockProcessingJob {
        private static final int TOTAL_FILES = 20;

        private final String id;
        private final Instant startedAt;
        private int processedFiles;

        MockProcessingJob(String id, Instant startedAt) {
            this.id = id;
            this.startedAt = startedAt;
        }

        synchronized ProcessingJobResponse advanceAndSnapshot() {
            processedFiles = Math.min(processedFiles + 5, TOTAL_FILES);

            int failedFiles = processedFiles == TOTAL_FILES ? 1 : 0;
            int skippedFiles = processedFiles == TOTAL_FILES ? 1 : 0;
            int successfulFiles = Math.max(processedFiles - failedFiles - skippedFiles, 0);

            return new ProcessingJobResponse(
                    id,
                    "Indexing mocked documents",
                    status(),
                    processedFiles * 100 / TOTAL_FILES,
                    processedFiles,
                    TOTAL_FILES,
                    successfulFiles,
                    failedFiles,
                    skippedFiles,
                    currentStep(),
                    startedAt
            );
        }

        private String status() {
            if (processedFiles == 0) {
                return "QUEUED";
            }

            if (processedFiles == TOTAL_FILES) {
                return "COMPLETED_WITH_ERRORS";
            }

            return "RUNNING";
        }

        private String currentStep() {
            if (processedFiles == TOTAL_FILES) {
                return "Mock run completed with one failed file and one skipped file";
            }

            if (processedFiles <= 5) {
                return "Preparing mocked file list";
            }

            return "Updating mocked processing counters";
        }
    }
}
