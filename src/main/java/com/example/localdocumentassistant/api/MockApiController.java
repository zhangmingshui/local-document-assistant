package com.example.localdocumentassistant.api;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class MockApiController {

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
                Instant.parse("2026-06-18T09:30:00Z")
        );
    }

    @PostMapping("/questions")
    public QuestionResponse askQuestion(@RequestBody QuestionRequest request) {
        String question = request.question() == null || request.question().isBlank()
                ? "your mocked question"
                : request.question();

        return new QuestionResponse(
                question,
                "This is a mocked answer for: \"" + question + "\". No files were read and no model was called.",
                List.of(
                        new CitationResponse("Mock Project Notes", "/Users/demo/Documents/Mock Research/project-notes.pdf"),
                        new CitationResponse("Mock Invoice Summary", "/Users/demo/Documents/Mock Invoices/invoice-summary.pdf")
                )
        );
    }

    public record FolderResponse(String id, String path, int documentCount) {
    }

    public record ProcessingJobResponse(
            String id,
            String name,
            String status,
            int progressPercent,
            int processedDocuments,
            int totalDocuments,
            Instant startedAt
    ) {
    }

    public record QuestionRequest(String question) {
    }

    public record QuestionResponse(String question, String answer, List<CitationResponse> citations) {
    }

    public record CitationResponse(String title, String path) {
    }
}
