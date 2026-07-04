package com.example.localdocumentassistant.api;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.localdocumentassistant.documentsource.DocumentSourceService;
import com.example.localdocumentassistant.documentsource.DocumentSourceService.DocumentSourceSummary;
import com.example.localdocumentassistant.documentsource.DuplicateDocumentSourceException;
import com.example.localdocumentassistant.documentcatalog.DocumentQueryService;
import com.example.localdocumentassistant.ingestion.IngestionJobService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class LocalDocumentAssistantController {

    private final DocumentSourceService documentSourceService;
    private final DocumentQueryService documentQueryService;
    private final IngestionJobService ingestionJobService;

    public LocalDocumentAssistantController(
            DocumentSourceService documentSourceService,
            DocumentQueryService documentQueryService,
            IngestionJobService ingestionJobService
    ) {
        this.documentSourceService = documentSourceService;
        this.documentQueryService = documentQueryService;
        this.ingestionJobService = ingestionJobService;
    }

    @GetMapping("/folders")
    public List<DocumentSourceSummary> folders() {
        return documentSourceService.getConfiguredSources();
    }

    @GetMapping("/folders/{folderId}/documents")
    public ResponseEntity<?> documents(
            @PathVariable Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int pageSize
    ) {
        if (page < 0 || pageSize < 1 || pageSize > 100) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Page must be zero or greater and pageSize must be between 1 and 100."));
        }

        return documentQueryService.getDocuments(folderId, page, pageSize)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No configured folder found for id: " + folderId)));
    }

    @GetMapping("/processing-jobs/latest")
    public ResponseEntity<?> latestProcessingJob() {
        return ingestionJobService.getLatestProcessingJobStatus()
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No processing jobs found.")));
    }

    @PostMapping("/processing-jobs")
    public ResponseEntity<?> startProcessingJob(
            @RequestBody StartProcessingJobRequest request
    ) {
        if (request == null || request.path() == null || request.path().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Document source path must not be blank."));
        }

        StartProcessingJobRequest trimmedRequest = new StartProcessingJobRequest(
                request.path().trim(),
                request.includeSubfolders()
        );

        try {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ingestionJobService.startProcessingJob(trimmedRequest));
        } catch (DuplicateDocumentSourceException duplicateError) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(duplicateError.getMessage()));
        }
    }

    @PostMapping("/folders/{folderId}/processing-jobs")
    public ResponseEntity<?> startProcessingJobForExistingFolder(@PathVariable Long folderId) {
        return ingestionJobService.startProcessingJobForExistingSource(folderId)
                .<ResponseEntity<?>>map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No configured folder found for id: " + folderId)));
    }

    @GetMapping("/processing-jobs/{jobId}")
    public ResponseEntity<?> processingJob(@PathVariable String jobId) {
        return ingestionJobService.pollProcessingJobStatus(jobId)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok(job))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No processing job found for id: " + jobId)));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> askQuestion(@RequestBody QuestionRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Question must not be blank."));
        }

        String question = request.question().trim();

        QuestionResponse response = new QuestionResponse(
                "Prototype response for \"" + question
                        + "\". Document retrieval and model calls are not implemented yet, "
                        + "so this answer is not generated from your documents.",
                List.of(
                        new SourceResponse(
                                "example-notes.txt",
                                "/prototype/examples/example-notes.txt",
                                1,
                                "Placeholder source only. Document retrieval is not implemented yet."
                        ),
                        new SourceResponse(
                                "example-summary.txt",
                                "/prototype/examples/example-summary.txt",
                                1,
                                "Placeholder source only. Model-generated answers are not implemented yet."
                        )
                )
        );

        return ResponseEntity.ok(response);
    }

    public record ProcessingJobResponse(
            String id,
            String name,
            String status,
            int progressPercent,
            int processedFiles,
            int totalFiles,
            int successfulFiles,
            int failedFiles,
            int skippedFiles,
            String currentStep,
            Instant startedAt
    ) {
    }

    public record StartProcessingJobRequest(String path, boolean includeSubfolders) {
    }

    public record StartProcessingJobResponse(String jobId, String status, String message, String pollUrl) {
    }

    public record QuestionRequest(String question) {
    }

    public record QuestionResponse(String answer, List<SourceResponse> sources) {
    }

    public record SourceResponse(String fileName, String filePath, int chunkNumber, String text) {
    }

    public record ErrorResponse(String message) {
    }
}
