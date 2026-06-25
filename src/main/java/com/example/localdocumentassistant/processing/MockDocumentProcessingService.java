package com.example.localdocumentassistant.processing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
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

    public MockDocumentProcessingService(ProcessingJobRepository processingJobRepository) {
        this.processingJobRepository = processingJobRepository;
    }

    public void startMockProcessing(String jobId, DocumentSource source) {
        executorService.submit(() -> runMockProcessing(jobId, source));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdownNow();
    }

    private void runMockProcessing(String jobId, DocumentSource source) {
        if (!scanConfiguredFolder(jobId, source)) {
            return;
        }

        int[] checkpoints = {5, 10, 15, 20};

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

    private boolean scanConfiguredFolder(String jobId, DocumentSource source) {
        Path sourcePath;
        try {
            sourcePath = Path.of(source.path());
        } catch (InvalidPathException invalidPath) {
            failJob(jobId, "Configured document source path is invalid: " + source.path(), invalidPath);
            return false;
        }

        if (!Files.exists(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            failJob(jobId, "Configured document source path does not exist: " + sourcePath, null);
            return false;
        }

        if (!Files.isDirectory(sourcePath, LinkOption.NOFOLLOW_LINKS)) {
            failJob(jobId, "Configured document source path is not a directory: " + sourcePath, null);
            return false;
        }

        if (!Files.isReadable(sourcePath)) {
            failJob(jobId, "Configured document source directory cannot be read: " + sourcePath, null);
            return false;
        }

        int maxDepth = source.includeSubfolders() ? Integer.MAX_VALUE : 1;
        LOGGER.info("Scanning configured document source path={} includeSubfolders={}",
                sourcePath, source.includeSubfolders());

        try (Stream<Path> paths = Files.walk(sourcePath, maxDepth)) {
            long discoveredFiles = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isSupportedDocumentFile)
                    .peek(this::logDiscoveredFileMetadata)
                    .count();

            LOGGER.info("Discovered {} supported .doc/.txt file(s) under {}", discoveredFiles, sourcePath);
            return true;
        } catch (IOException | UncheckedIOException | SecurityException scanError) {
            failJob(jobId, "Configured document source directory could not be scanned: " + sourcePath, scanError);
            return false;
        }
    }

    private boolean isSupportedDocumentFile(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        return fileName.endsWith(".doc") || fileName.endsWith(".txt");
    }

    private void logDiscoveredFileMetadata(Path file) {
        try {
            String fileName = file.getFileName().toString();
            LOGGER.info(
                    "Discovered document file path={} fileName={} fileType={} fileSize={} lastModifiedAt={}",
                    file.toAbsolutePath(),
                    fileName,
                    extensionFor(fileName),
                    Files.size(file),
                    Files.getLastModifiedTime(file).toInstant()
            );
        } catch (IOException metadataError) {
            throw new UncheckedIOException("Could not read metadata for discovered file: " + file, metadataError);
        }
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
        int failedFiles = complete ? 1 : 0;
        int skippedFiles = complete ? 1 : 0;
        int successfulFiles = Math.max(processedFiles - failedFiles - skippedFiles, 0);

        return new ProcessingJob(
                currentJob.id(),
                currentJob.jobId(),
                currentJob.watchedFolderId(),
                complete ? ProcessingJobStatus.COMPLETED_WITH_ERRORS : ProcessingJobStatus.RUNNING,
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

    private String currentStepFor(int processedFiles, int totalFiles) {
        if (processedFiles >= totalFiles) {
            return "Mock run completed with one failed file and one skipped file";
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
}
