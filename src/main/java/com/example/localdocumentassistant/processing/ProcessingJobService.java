package com.example.localdocumentassistant.processing;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.api.MockApiController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobResponse;

@Service
public class ProcessingJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingJobService.class);

    private final InMemoryDocumentSourceRepository documentSourceRepository;
    private final InMemoryProcessingJobRepository processingJobRepository;
    private final AtomicInteger jobSequence = new AtomicInteger();

    public ProcessingJobService(
            InMemoryDocumentSourceRepository documentSourceRepository,
            InMemoryProcessingJobRepository processingJobRepository
    ) {
        this.documentSourceRepository = documentSourceRepository;
        this.processingJobRepository = processingJobRepository;
    }

    public StartProcessingJobResponse startProcessingJob(StartProcessingJobRequest request) {
        DocumentSource source = new DocumentSource(request.path(), request.includeSubfolders(), "PROCESSING");
        documentSourceRepository.save(source);
        LOGGER.info("Saved mocked document source path={} includeSubfolders={} status={}",
                source.path(), source.includeSubfolders(), source.status());

        String jobId = "mock-job-%03d".formatted(jobSequence.incrementAndGet());
        processingJobRepository.save(new MockProcessingJob(jobId, Instant.now()));

        return new StartProcessingJobResponse(
                jobId,
                "QUEUED",
                "Mock processing job started. No files are being scanned yet.",
                "/api/processing-jobs/" + jobId
        );
    }

    public Optional<ProcessingJobResponse> pollProcessingJobStatus(String jobId) {
        return processingJobRepository.findById(jobId)
                .map(MockProcessingJob::advanceAndSnapshot);
    }

    static class MockProcessingJob {
        private static final int TOTAL_FILES = 20;

        private final String id;
        private final Instant startedAt;
        private int processedFiles;

        MockProcessingJob(String id, Instant startedAt) {
            this.id = id;
            this.startedAt = startedAt;
        }

        String id() {
            return id;
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
