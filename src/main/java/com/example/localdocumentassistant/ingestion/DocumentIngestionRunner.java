package com.example.localdocumentassistant.ingestion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentsource.DocumentSource;

import jakarta.annotation.PreDestroy;

@Service
public class DocumentIngestionRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentIngestionRunner.class);
    private static final String DIRECTORY_READ_FAILURE_MESSAGE =
            "Could not read the selected directory. Check that the folder exists and that the app has permission to access it.";

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final IngestionJobRepository ingestionJobRepository;
    private final DocumentInventoryService documentInventoryService;
    private final DocumentTextProcessingService documentTextProcessingService;

    public DocumentIngestionRunner(
            IngestionJobRepository ingestionJobRepository,
            DocumentInventoryService documentInventoryService,
            DocumentTextProcessingService documentTextProcessingService
    ) {
        this.ingestionJobRepository = ingestionJobRepository;
        this.documentInventoryService = documentInventoryService;
        this.documentTextProcessingService = documentTextProcessingService;
    }

    public void startIngestion(String jobId, DocumentSource source) {
        executorService.submit(() -> runIngestion(jobId, source));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    void runIngestion(String jobId, DocumentSource source) {
        IngestionJob currentJob = updateJobForScanning(jobId);
        if (currentJob == null) {
            return;
        }

        List<DiscoveredDocumentMetadata> discoveredDocuments = scanConfiguredFolder(jobId, source);
        if (discoveredDocuments == null) {
            return;
        }

        currentJob = updateCurrentStep(currentJob, "Checking document inventory and content changes");
        try {
            documentInventoryService.persistDiscoveredDocuments(source, discoveredDocuments);
        } catch (UncheckedIOException | SecurityException hashingError) {
            failJob(jobId, "Could not calculate a content hash for a discovered document.", hashingError);
            return;
        }

        List<Document> documentsToProcess = documentTextProcessingService
                .findDocumentsNeedingProcessing(source.id());
        currentJob = initializeDocumentProcessing(currentJob, documentsToProcess.size());

        if (documentsToProcess.isEmpty()) {
            completeJob(currentJob);
            return;
        }

        for (Document document : documentsToProcess) {
            currentJob = markCurrentDocument(currentJob, document);
            DocumentProcessingOutcome outcome = documentTextProcessingService.processDocument(document);
            currentJob = recordDocumentOutcome(currentJob, document, outcome);
        }

        completeJob(currentJob);
    }

    private List<DiscoveredDocumentMetadata> scanConfiguredFolder(String jobId, DocumentSource source) {
        Path sourcePath;
        try {
            sourcePath = Path.of(source.path());
        } catch (InvalidPathException invalidPath) {
            failJob(jobId, "Configured document source path is invalid: " + source.path(), invalidPath);
            return null;
        }

        if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            failJob(jobId, "Configured document source path does not exist: " + sourcePath, null);
            return null;
        }

        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            failJob(jobId, "Configured document source path is not a directory: " + sourcePath, null);
            return null;
        }

        if (!Files.isReadable(sourcePath)) {
            failJob(jobId, "Configured document source directory cannot be read: " + sourcePath, null);
            return null;
        }

        int maxDepth = source.includeSubfolders() ? Integer.MAX_VALUE : 1;
        LOGGER.info("Scanning configured document source path={} includeSubfolders={}",
                sourcePath, source.includeSubfolders());

        try (Stream<Path> paths = Files.walk(sourcePath, maxDepth)) {
            List<DiscoveredDocumentMetadata> discoveredDocuments = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isSupportedDocumentFile)
                    .map(this::readDiscoveredFileMetadata)
                    .toList();

            LOGGER.info("Discovered {} supported .doc/.txt file(s) under {}", discoveredDocuments.size(), sourcePath);
            return discoveredDocuments;
        } catch (IOException | UncheckedIOException | SecurityException scanError) {
            failJob(jobId, "Configured document source directory could not be scanned: " + sourcePath, scanError);
            return null;
        }
    }

    private boolean isSupportedDocumentFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".doc") || fileName.endsWith(".txt");
    }

    private DiscoveredDocumentMetadata readDiscoveredFileMetadata(Path file) {
        try {
            String fileName = file.getFileName().toString();
            String fileType = extensionFor(fileName);
            long fileSize = Files.size(file);
            String lastModifiedAt = Files.getLastModifiedTime(file).toInstant().toString();
            LOGGER.info(
                    "Discovered document file path={} fileName={} fileType={} fileSize={} lastModifiedAt={}",
                    file.toAbsolutePath(),
                    fileName,
                    fileType,
                    fileSize,
                    lastModifiedAt
            );

            return new DiscoveredDocumentMetadata(
                    file,
                    file.toAbsolutePath().toString(),
                    fileName,
                    fileType,
                    fileSize,
                    lastModifiedAt
            );
        } catch (IOException metadataError) {
            throw new UncheckedIOException("Could not read metadata for discovered file: " + file, metadataError);
        }
    }

    private IngestionJob updateJobForScanning(String jobId) {
        IngestionJob currentJob = ingestionJobRepository.findByJobId(jobId).orElse(null);
        if (currentJob == null) {
            LOGGER.warn("Could not start ingestion because job {} was not found.", jobId);
            return null;
        }

        return ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                IngestionJobStatus.RUNNING,
                0,
                0,
                0,
                0,
                0,
                null,
                "Scanning configured folder",
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                null
        ));
    }

    private IngestionJob updateCurrentStep(IngestionJob currentJob, String currentStep) {
        return ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                currentJob.status(),
                currentJob.totalFiles(),
                currentJob.processedFiles(),
                currentJob.successfulFiles(),
                currentJob.failedFiles(),
                currentJob.skippedFiles(),
                currentJob.currentFile(),
                currentStep,
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                currentJob.completedAt()
        ));
    }

    private IngestionJob initializeDocumentProcessing(IngestionJob currentJob, int totalFiles) {
        return ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                IngestionJobStatus.RUNNING,
                totalFiles,
                0,
                0,
                0,
                0,
                null,
                "Processing documents that need work",
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                null
        ));
    }

    private IngestionJob markCurrentDocument(IngestionJob currentJob, Document document) {
        return ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                IngestionJobStatus.RUNNING,
                currentJob.totalFiles(),
                currentJob.processedFiles(),
                currentJob.successfulFiles(),
                currentJob.failedFiles(),
                currentJob.skippedFiles(),
                document.filePath(),
                "Processing " + document.fileName(),
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                null
        ));
    }

    private IngestionJob recordDocumentOutcome(
            IngestionJob currentJob,
            Document document,
            DocumentProcessingOutcome outcome
    ) {
        int successfulFiles = currentJob.successfulFiles()
                + (outcome == DocumentProcessingOutcome.SUCCESSFUL ? 1 : 0);
        int failedFiles = currentJob.failedFiles()
                + (outcome == DocumentProcessingOutcome.FAILED ? 1 : 0);
        int skippedFiles = currentJob.skippedFiles()
                + (outcome == DocumentProcessingOutcome.SKIPPED ? 1 : 0);

        String currentStep = switch (outcome) {
            case SUCCESSFUL -> "Indexed " + document.fileName();
            case SKIPPED -> "No extractor available for " + document.fileName();
            case FAILED -> "Could not process " + document.fileName();
        };

        return ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                IngestionJobStatus.RUNNING,
                currentJob.totalFiles(),
                currentJob.processedFiles() + 1,
                successfulFiles,
                failedFiles,
                skippedFiles,
                document.filePath(),
                currentStep,
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                null
        ));
    }

    private void completeJob(IngestionJob currentJob) {
        boolean completedWithErrors = currentJob.failedFiles() > 0;
        String currentStep;
        if (currentJob.totalFiles() == 0) {
            currentStep = "No documents needed processing";
        } else if (completedWithErrors) {
            currentStep = "Processing completed with errors";
        } else {
            currentStep = "Processing completed";
        }

        ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                completedWithErrors
                        ? IngestionJobStatus.COMPLETED_WITH_ERRORS
                        : IngestionJobStatus.COMPLETED,
                currentJob.totalFiles(),
                currentJob.processedFiles(),
                currentJob.successfulFiles(),
                currentJob.failedFiles(),
                currentJob.skippedFiles(),
                null,
                currentStep,
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                Instant.now().toString()
        ));
    }

    private String extensionFor(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex < 0 || extensionIndex == fileName.length() - 1) {
            return "";
        }

        return fileName.substring(extensionIndex + 1).toLowerCase();
    }

    private void failJob(String jobId, String message, Exception error) {
        if (error == null) {
            LOGGER.warn(message);
        } else {
            LOGGER.warn(message, error);
        }

        IngestionJob currentJob = ingestionJobRepository.findByJobId(jobId).orElse(null);
        if (currentJob == null) {
            LOGGER.warn("Could not mark job {} as failed because it was not found.", jobId);
            return;
        }

        ingestionJobRepository.update(new IngestionJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                IngestionJobStatus.FAILED,
                currentJob.totalFiles(),
                currentJob.processedFiles(),
                currentJob.successfulFiles(),
                currentJob.failedFiles(),
                currentJob.skippedFiles(),
                currentJob.currentFile(),
                DIRECTORY_READ_FAILURE_MESSAGE,
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                Instant.now().toString()
        ));
    }

}
