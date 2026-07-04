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
import com.example.localdocumentassistant.source.DocumentSource;
import com.example.localdocumentassistant.source.DocumentSourceRepository;

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

        return startProcessingForSource(source);
    }

    public Optional<StartProcessingJobResponse> startProcessingJobForExistingSource(Long sourceId) {
        return documentSourceRepository.findById(sourceId)
                .map(this::startProcessingForSource);
    }

    private StartProcessingJobResponse startProcessingForSource(DocumentSource source) {
        String jobId = "job-" + UUID.randomUUID();
        ProcessingJob job = processingJobRepository.create(new ProcessingJob(
                null,
                jobId,
                source.id(),
                ProcessingJobStatus.PENDING,
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
        mockDocumentProcessingService.startMockProcessing(job.jobId(), source);

        return new StartProcessingJobResponse(
                job.jobId(),
                job.status().name(),
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
                "Indexing discovered documents",
                job.status().name(),
                progressPercent(job),
                job.processedFiles(),
                job.totalFiles(),
                job.successfulFiles(),
                job.failedFiles(),
                job.skippedFiles(),
                job.currentStep(),
                Instant.parse(job.startedAt())
        );
    }

    private int progressPercent(ProcessingJob job) {
        if (job.totalFiles() == 0) {
            return switch (job.status()) {
                case COMPLETED, COMPLETED_WITH_ERRORS -> 100;
                default -> 0;
            };
        }

        return Math.min(100, job.processedFiles() * 100 / job.totalFiles());
    }
}
