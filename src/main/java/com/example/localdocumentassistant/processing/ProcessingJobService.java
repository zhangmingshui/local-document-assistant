package com.example.localdocumentassistant.processing;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.api.MockApiController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobResponse;

@Service
public class ProcessingJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcessingJobService.class);
    private static final int MOCK_TOTAL_FILES = 20;

    private final DocumentSourceRepository documentSourceRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final MockDocumentProcessingService mockDocumentProcessingService;

    public ProcessingJobService(
            DocumentSourceRepository documentSourceRepository,
            ProcessingJobRepository processingJobRepository,
            MockDocumentProcessingService mockDocumentProcessingService
    ) {
        this.documentSourceRepository = documentSourceRepository;
        this.processingJobRepository = processingJobRepository;
        this.mockDocumentProcessingService = mockDocumentProcessingService;
    }

    public StartProcessingJobResponse startProcessingJob(StartProcessingJobRequest request) {
        DocumentSource source = documentSourceRepository.create(
                new DocumentSource(null, request.path(), request.includeSubfolders(), "CONFIGURED")
        );
        LOGGER.info("Registered SQLite document source id={} path={} includeSubfolders={} status={}",
                source.id(), source.path(), source.includeSubfolders(), source.status());

        String jobId = "job-" + UUID.randomUUID();
        ProcessingJob job = processingJobRepository.create(new ProcessingJob(
                null,
                jobId,
                source.id(),
                "PENDING",
                MOCK_TOTAL_FILES,
                0,
                0,
                0,
                0,
                null,
                "Waiting to start mocked processing",
                0,
                0,
                Instant.now().toString(),
                null
        ));
        mockDocumentProcessingService.startMockProcessing(job.jobId());

        return new StartProcessingJobResponse(
                job.jobId(),
                job.status(),
                "Mock processing job started. No files are being scanned yet.",
                "/api/processing-jobs/" + job.jobId()
        );
    }

    public Optional<ProcessingJobResponse> pollProcessingJobStatus(String jobId) {
        return processingJobRepository.findByJobId(jobId)
                .map(this::toResponse);
    }

    private ProcessingJobResponse toResponse(ProcessingJob job) {
        return new ProcessingJobResponse(
                job.jobId(),
                "Indexing mocked documents",
                job.status(),
                job.processedFiles() * 100 / job.totalFiles(),
                job.processedFiles(),
                job.totalFiles(),
                job.successfulFiles(),
                job.failedFiles(),
                job.skippedFiles(),
                job.currentStep(),
                Instant.parse(job.startedAt())
        );
    }
}
