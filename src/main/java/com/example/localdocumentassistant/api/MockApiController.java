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
import org.springframework.web.bind.annotation.RestController;

import com.example.localdocumentassistant.processing.ProcessingJobService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class MockApiController {

    private final ProcessingJobService processingJobService;

    public MockApiController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    @GetMapping("/folders")
    public List<FolderResponse> folders() {
        return List.of(
                new FolderResponse("folder-1", "/Users/demo/Documents/Mock Research", 42),
                new FolderResponse("folder-2", "/Users/demo/Documents/Mock Invoices", 18),
                new FolderResponse("folder-3", "/Users/demo/Desktop/Mock Notes", 9)
        );
    }

    @GetMapping("/processing-jobs/latest")
    public ProcessingJobResponse latestProcessingJob() {
        return new ProcessingJobResponse(
                "job-2026-001",
                "Indexing mocked documents",
                "RUNNING",
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
    public ResponseEntity<StartProcessingJobResponse> startProcessingJob(
            @RequestBody StartProcessingJobRequest request
    ) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(processingJobService.startProcessingJob(request));
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

    public record FolderResponse(String id, String path, int documentCount) {
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
