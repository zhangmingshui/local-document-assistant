package com.example.localdocumentassistant.ingestion;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.api.LocalDocumentAssistantController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.StartProcessingJobResponse;
import com.example.localdocumentassistant.documentsource.DocumentSource;
import com.example.localdocumentassistant.documentsource.DocumentSourceRepository;

@Service
public class IngestionJobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestionJobService.class);
    private final DocumentSourceRepository documentSourceRepository;
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentIngestionRunner documentIngestionRunner;

    public IngestionJobService(
            DocumentSourceRepository documentSourceRepository,
            IngestionJobRepository ingestionJobRepository,
            DocumentIngestionRunner documentIngestionRunner
    ) {
        this.documentSourceRepository = documentSourceRepository;
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentIngestionRunner = documentIngestionRunner;
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
        IngestionJob job = ingestionJobRepository.create(new IngestionJob(
                null,
                jobId,
                source.id(),
                IngestionJobStatus.PENDING,
                0,
                0,
                0,
                0,
                0,
                null,
                "Waiting to start processing",
                0,
                0,
                Instant.now().toString(),
                null
        ));
        documentIngestionRunner.startIngestion(job.jobId(), source);

        return new StartProcessingJobResponse(
                job.jobId(),
                job.status().name(),
                "Processing job started.",
                "/api/processing-jobs/" + job.jobId()
        );
    }

    public Optional<ProcessingJobResponse> pollProcessingJobStatus(String jobId) {
        return ingestionJobRepository.findByJobId(jobId)
                .map(this::toResponse);
    }

    public Optional<ProcessingJobResponse> getLatestProcessingJobStatus() {
        return ingestionJobRepository.findLatest()
                .map(this::toResponse);
    }

    private ProcessingJobResponse toResponse(IngestionJob job) {
        return new ProcessingJobResponse(
                job.jobId(),
                "Processing documents",
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

    private int progressPercent(IngestionJob job) {
        if (job.totalFiles() == 0) {
            return switch (job.status()) {
                case COMPLETED, COMPLETED_WITH_ERRORS -> 100;
                default -> 0;
            };
        }

        return Math.min(100, job.processedFiles() * 100 / job.totalFiles());
    }
}
