package com.example.localdocumentassistant.processing;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;

@Service
public class MockDocumentProcessingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MockDocumentProcessingService.class);
    private static final String DIRECTORY_READ_FAILURE_MESSAGE =
            "Could not read the selected directory. Check that the folder exists and that the app has permission to access it.";

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentRepository documentRepository;

    public MockDocumentProcessingService(
            ProcessingJobRepository processingJobRepository,
            DocumentRepository documentRepository
    ) {
        this.processingJobRepository = processingJobRepository;
        this.documentRepository = documentRepository;
    }

    public void startMockProcessing(String jobId, DocumentSource source) {
        executorService.submit(() -> runMockProcessing(jobId, source));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runMockProcessing(String jobId, DocumentSource source) {
        List<DiscoveredDocumentMetadata> discoveredDocuments = scanConfiguredFolder(jobId, source);
        if (discoveredDocuments == null) {
            return;
        }

        try {
            persistDiscoveredDocuments(source, discoveredDocuments);
        } catch (UncheckedIOException | SecurityException hashingError) {
            failJob(jobId, "Could not calculate a content hash for a discovered document.", hashingError);
            return;
        }
        ProcessingJob discoveredJob = updateTotalFiles(jobId, discoveredDocuments.size());
        if (discoveredJob == null) {
            return;
        }

        if (discoveredDocuments.isEmpty()) {
            processingJobRepository.update(completedJobState(
                    discoveredJob,
                    0,
                    "Mock run completed. No supported .doc/.txt files were discovered."
            ));
            return;
        }

        int[] checkpoints = progressCheckpoints(discoveredDocuments.size());

        for (int processedFiles : checkpoints) {
            if (!sleepBetweenMockUpdates()) {
                return;
            }

            ProcessingJob currentJob = processingJobRepository.findByJobId(jobId).orElse(null);
            if (currentJob == null) {
                LOGGER.warn("Stopping mocked processing because job {} was not found.", jobId);
                return;
            }

            processingJobRepository.update(nextMockState(currentJob, processedFiles));
        }
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

    private void persistDiscoveredDocuments(
            DocumentSource source,
            List<DiscoveredDocumentMetadata> discoveredDocuments
    ) {
        for (DiscoveredDocumentMetadata discoveredDocument : discoveredDocuments) {
            Optional<Document> existingDocument = documentRepository
                    .findByWatchedFolderIdAndFilePath(source.id(), discoveredDocument.filePath());

            if (existingDocument.filter(document -> metadataAndHashAreUnchanged(document, discoveredDocument))
                    .isPresent()) {
                LOGGER.info("Skipped content hash for unchanged document path={}", discoveredDocument.filePath());
                continue;
            }

            String contentHash = calculateSha256(discoveredDocument.path());
            Document document = existingDocument
                    .map(existing -> documentFromExisting(existing, discoveredDocument, contentHash))
                    .orElseGet(() -> newDocument(source, discoveredDocument, contentHash));

            Document savedDocument = document.id() == null
                    ? documentRepository.create(document)
                    : documentRepository.update(document);
            LOGGER.info("Saved discovered document metadata id={} uuid={} path={}",
                    savedDocument.id(), savedDocument.documentUuid(), savedDocument.filePath());
        }
    }

    private boolean metadataAndHashAreUnchanged(
            Document existingDocument,
            DiscoveredDocumentMetadata discoveredDocument
    ) {
        return existingDocument.contentHash() != null
                && Objects.equals(existingDocument.fileSize(), discoveredDocument.fileSize())
                && Objects.equals(existingDocument.lastModifiedAt(), discoveredDocument.lastModifiedAt());
    }

    private String calculateSha256(Path file) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException unavailableAlgorithm) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", unavailableAlgorithm);
        }

        byte[] buffer = new byte[8192];
        try (InputStream inputStream = Files.newInputStream(file)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        } catch (IOException readError) {
            throw new UncheckedIOException("Could not hash discovered document: " + file, readError);
        }

        return HexFormat.of().formatHex(digest.digest());
    }

    private Document newDocument(
            DocumentSource source,
            DiscoveredDocumentMetadata discoveredDocument,
            String contentHash
    ) {
        return new Document(
                null,
                null,
                source.id(),
                discoveredDocument.filePath(),
                discoveredDocument.fileName(),
                discoveredDocument.fileType(),
                discoveredDocument.fileSize(),
                discoveredDocument.lastModifiedAt(),
                contentHash,
                DocumentProcessingStatus.NEEDS_PROCESSING,
                0,
                null
        );
    }

    private Document documentFromExisting(
            Document existingDocument,
            DiscoveredDocumentMetadata discoveredDocument,
            String contentHash
    ) {
        DocumentProcessingStatus processingStatus = existingDocument.contentHash() == null
                || !existingDocument.contentHash().equals(contentHash)
                ? DocumentProcessingStatus.NEEDS_PROCESSING
                : existingDocument.processingStatus();

        return new Document(
                existingDocument.id(),
                existingDocument.documentUuid(),
                existingDocument.watchedFolderId(),
                discoveredDocument.filePath(),
                discoveredDocument.fileName(),
                discoveredDocument.fileType(),
                discoveredDocument.fileSize(),
                discoveredDocument.lastModifiedAt(),
                contentHash,
                processingStatus,
                existingDocument.chunkCount(),
                existingDocument.lastProcessedAt()
        );
    }

    private ProcessingJob updateTotalFiles(String jobId, int totalFiles) {
        ProcessingJob currentJob = processingJobRepository.findByJobId(jobId).orElse(null);
        if (currentJob == null) {
            LOGGER.warn("Could not update total files because job {} was not found.", jobId);
            return null;
        }

        return processingJobRepository.update(new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                ProcessingJobStatus.RUNNING,
                totalFiles,
                0,
                0,
                0,
                0,
                currentJob.currentFile(),
                "Discovered " + totalFiles + " supported .doc/.txt file(s)",
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                null
        ));
    }

    private int[] progressCheckpoints(int totalFiles) {
        return Stream.of(
                        Math.max(1, totalFiles / 4),
                        Math.max(1, totalFiles / 2),
                        Math.max(1, totalFiles * 3 / 4),
                        totalFiles
                )
                .mapToInt(Integer::intValue)
                .distinct()
                .toArray();
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

        ProcessingJob currentJob = processingJobRepository.findByJobId(jobId).orElse(null);
        if (currentJob == null) {
            LOGGER.warn("Could not mark job {} as failed because it was not found.", jobId);
            return;
        }

        processingJobRepository.update(new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                ProcessingJobStatus.FAILED,
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

    private ProcessingJob nextMockState(ProcessingJob currentJob, int processedFiles) {
        boolean complete = processedFiles >= currentJob.totalFiles();
        int failedFiles = complete && currentJob.totalFiles() >= 2 ? 1 : 0;
        int skippedFiles = complete && currentJob.totalFiles() >= 3 ? 1 : 0;
        int successfulFiles = Math.max(processedFiles - failedFiles - skippedFiles, 0);

        return new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                statusForMockProgress(complete, failedFiles, skippedFiles),
                currentJob.totalFiles(),
                processedFiles,
                successfulFiles,
                failedFiles,
                skippedFiles,
                currentJob.currentFile(),
                currentStepFor(processedFiles, currentJob.totalFiles()),
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                complete ? Instant.now().toString() : null
        );
    }

    private ProcessingJob completedJobState(ProcessingJob currentJob, int processedFiles, String currentStep) {
        return new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                ProcessingJobStatus.COMPLETED,
                currentJob.totalFiles(),
                processedFiles,
                processedFiles,
                0,
                0,
                currentJob.currentFile(),
                currentStep,
                currentJob.currentChunk(),
                currentJob.totalChunksForCurrentFile(),
                currentJob.startedAt(),
                Instant.now().toString()
        );
    }

    private ProcessingJobStatus statusForMockProgress(boolean complete, int failedFiles, int skippedFiles) {
        if (!complete) {
            return ProcessingJobStatus.RUNNING;
        }

        return failedFiles > 0 || skippedFiles > 0
                ? ProcessingJobStatus.COMPLETED_WITH_ERRORS
                : ProcessingJobStatus.COMPLETED;
    }

    private String currentStepFor(int processedFiles, int totalFiles) {
        if (processedFiles >= totalFiles) {
            if (totalFiles >= 3) {
                return "Mock run completed with one failed file and one skipped file";
            }

            if (totalFiles == 2) {
                return "Mock run completed with one failed file";
            }

            return "Mock run completed";
        }

        if (processedFiles <= 5) {
            return "Preparing mocked file list";
        }

        return "Updating mocked processing counters";
    }

    private boolean sleepBetweenMockUpdates() {
        try {
            Thread.sleep(1500);
            return true;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private record DiscoveredDocumentMetadata(
            Path path,
            String filePath,
            String fileName,
            String fileType,
            long fileSize,
            String lastModifiedAt
    ) {
    }
}
