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

import com.example.localdocumentassistant.document.DocumentService;
import com.example.localdocumentassistant.processing.ProcessingJobService;
import com.example.localdocumentassistant.processing.ProcessingJobStatus;
import com.example.localdocumentassistant.source.DocumentSourceService;
import com.example.localdocumentassistant.source.DocumentSourceService.DocumentSourceSummary;
import com.example.localdocumentassistant.source.DuplicateDocumentSourceException;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class MockApiController {

    private final DocumentSourceService documentSourceService;
    private final DocumentService documentService;
    private final ProcessingJobService processingJobService;

    public MockApiController(
            DocumentSourceService documentSourceService,
            DocumentService documentService,
            ProcessingJobService processingJobService
    ) {
        this.documentSourceService = documentSourceService;
        this.documentService = documentService;
        this.processingJobService = processingJobService;
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

        return documentService.getDocuments(folderId, page, pageSize)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No configured folder found for id: " + folderId)));
    }

    @GetMapping("/processing-jobs/latest")
    public ProcessingJobResponse latestProcessingJob() {
        return new ProcessingJobResponse(
                "job-2026-001",
                "Indexing mocked documents",
                ProcessingJobStatus.RUNNING.name(),
                68,
                46,
                67,
                41,
                2,
                3,
                "Reading mocked metadata and preparing preview records",
                Instant.parse("2026-06-18T09:30:00Z")
        );
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
                    .body(processingJobService.startProcessingJob(trimmedRequest));
        } catch (DuplicateDocumentSourceException duplicateError) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(duplicateError.getMessage()));
        }
    }

    @PostMapping("/folders/{folderId}/processing-jobs")
    public ResponseEntity<?> startProcessingJobForExistingFolder(@PathVariable Long folderId) {
        return processingJobService.startProcessingJobForExistingSource(folderId)
                .<ResponseEntity<?>>map(response -> ResponseEntity.status(HttpStatus.ACCEPTED).body(response))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No configured folder found for id: " + folderId)));
    }

    @GetMapping("/processing-jobs/{jobId}")
    public ResponseEntity<?> processingJob(@PathVariable String jobId) {
        return processingJobService.pollProcessingJobStatus(jobId)
                .<ResponseEntity<?>>map(job -> ResponseEntity.ok(job))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(new ErrorResponse("No mocked processing job found for id: " + jobId)));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> askQuestion(@RequestBody QuestionRequest request) {
        if (request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Question must not be blank."));
        }

        String question = request.question().trim();

        QuestionResponse response = new QuestionResponse(
                "Based on mocked indexed documents, the answer to \"" + question
                        + "\" is that this prototype can show a realistic Q&A flow without retrieval or model calls.",
                List.of(
                        new SourceResponse(
                                "project-notes.pdf",
                                "/Users/demo/Documents/Mock Research/project-notes.pdf",
                                3,
                                "Mock excerpt: the local assistant should answer from indexed document snippets once real retrieval exists."
                        ),
                        new SourceResponse(
                                "invoice-summary.pdf",
                                "/Users/demo/Documents/Mock Invoices/invoice-summary.pdf",
                                1,
                                "Mock excerpt: source cards show where an answer might have come from after indexing is implemented."
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
