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
import org.springframework.web.client.RestClientException;

import com.example.localdocumentassistant.documentsource.DocumentSourceService;
import com.example.localdocumentassistant.documentsource.DocumentSourceService.DocumentSourceSummary;
import com.example.localdocumentassistant.documentsource.DuplicateDocumentSourceException;
import com.example.localdocumentassistant.documentcatalog.DocumentQueryService;
import com.example.localdocumentassistant.ingestion.IngestionJobService;
import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
import com.example.localdocumentassistant.indexing.DocumentSearchService;
import com.example.localdocumentassistant.questionanswering.ChatModelUnavailableException;
import com.example.localdocumentassistant.questionanswering.QuestionAnsweringResult;
import com.example.localdocumentassistant.questionanswering.QuestionAnsweringService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class LocalDocumentAssistantController {

    private final DocumentSourceService documentSourceService;
    private final DocumentQueryService documentQueryService;
    private final IngestionJobService ingestionJobService;
    private final DocumentSearchService documentSearchService;
    private final QuestionAnsweringService questionAnsweringService;

    public LocalDocumentAssistantController(
            DocumentSourceService documentSourceService,
            DocumentQueryService documentQueryService,
            IngestionJobService ingestionJobService,
            DocumentSearchService documentSearchService,
            QuestionAnsweringService questionAnsweringService
    ) {
        this.documentSourceService = documentSourceService;
        this.documentQueryService = documentQueryService;
        this.ingestionJobService = ingestionJobService;
        this.documentSearchService = documentSearchService;
        this.questionAnsweringService = questionAnsweringService;
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

    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest request) {
        if (request == null || request.query() == null || request.query().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Search query must not be blank."));
        }

        int limit = request.limit() == null ? 5 : request.limit();
        if (limit < 1 || limit > 50) {
            return ResponseEntity.badRequest().body(new ErrorResponse("Search limit must be between 1 and 50."));
        }

        List<SearchResultResponse> results = documentSearchService.search(request.query().trim(), limit).stream()
                .map(this::toSearchResultResponse)
                .toList();
        return ResponseEntity.ok(new SearchResponse(results));
    }

    @PostMapping("/questions")
    public ResponseEntity<?> askQuestion(@RequestBody QuestionRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("Question must not be blank."));
        }

        try {
            QuestionAnsweringResult result = questionAnsweringService.answer(request.question().trim());
            List<SourceResponse> sources = result.sources().stream()
                    .map(source -> new SourceResponse(
                            source.fileName(),
                            source.filePath(),
                            source.chunkNumber(),
                            source.text()
                    ))
                    .toList();
            return ResponseEntity.ok(new QuestionResponse(result.answer(), sources));
        } catch (ChatModelUnavailableException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(error.getMessage()));
        } catch (RestClientException error) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ErrorResponse(
                            "Question answering is unavailable. Check that Ollama and Chroma are running."
                    ));
        }
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

    public record SearchRequest(String query, Integer limit) {
    }

    public record SearchResponse(List<SearchResultResponse> results) {
    }

    public record SearchResultResponse(
            String text,
            String fileName,
            String filePath,
            int chunkIndex,
            Long documentId,
            String documentUuid
    ) {
    }

    public record ErrorResponse(String message) {
    }

    private SearchResultResponse toSearchResultResponse(DocumentSearchMatch match) {
        return new SearchResultResponse(
                match.text(),
                match.fileName(),
                match.filePath(),
                match.chunkIndex(),
                match.documentId(),
                match.documentUuid()
        );
    }
}
