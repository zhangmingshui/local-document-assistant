package com.example.localdocumentassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;
import com.example.localdocumentassistant.documentsource.DocumentSource;
import com.example.localdocumentassistant.indexing.DocumentIndexingService;
import com.example.localdocumentassistant.indexing.DocumentVectorStore;
import com.example.localdocumentassistant.persistence.JdbcDocumentRepository;
import com.example.localdocumentassistant.persistence.JdbcIngestionJobRepository;

class DocumentIngestionRunnerTest {

    private static final long WATCHED_FOLDER_ID = 42L;

    @TempDir
    Path tempDirectory;

    private DocumentRepository documentRepository;
    private IngestionJobRepository ingestionJobRepository;
    private DocumentIngestionRunner documentIngestionRunner;
    private DocumentSource documentSource;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("ingestion-runner-test.sqlite"));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createTables(jdbcTemplate);

        documentRepository = new JdbcDocumentRepository(jdbcTemplate);
        ingestionJobRepository = new JdbcIngestionJobRepository(jdbcTemplate);
        DocumentInventoryService inventoryService = new DocumentInventoryService(documentRepository);
        DocumentIndexingService indexingService = new DocumentIndexingService(
                documentRepository,
                text -> List.of(0.1, 0.2),
                mock(DocumentVectorStore.class)
        );
        DocumentTextProcessingService textProcessingService = new DocumentTextProcessingService(
                documentRepository,
                List.of(new PlainTextExtractor()),
                new FixedSizeTextChunker(5),
                indexingService
        );
        documentIngestionRunner = new DocumentIngestionRunner(
                ingestionJobRepository,
                inventoryService,
                textProcessingService
        );
        documentSource = new DocumentSource(WATCHED_FOLDER_ID, tempDirectory.toString(), true, "CONFIGURED");
    }

    @AfterEach
    void tearDown() {
        documentIngestionRunner.shutdown();
    }

    @Test
    void txtFileProducesRealSuccessfulProgress() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("notes.txt"), "Hello local assistant");
        IngestionJob job = createPendingJob("job-txt");

        documentIngestionRunner.runIngestion(job.jobId(), documentSource);

        IngestionJob completedJob = ingestionJobRepository.findByJobId(job.jobId()).orElseThrow();
        assertThat(completedJob.totalFiles()).isEqualTo(1);
        assertThat(completedJob.processedFiles()).isEqualTo(1);
        assertThat(completedJob.successfulFiles()).isEqualTo(1);
        assertThat(completedJob.failedFiles()).isZero();
        assertThat(completedJob.skippedFiles()).isZero();
        assertThat(completedJob.status()).isEqualTo(IngestionJobStatus.COMPLETED);
        assertThat(completedJob.currentStep()).isEqualTo("Processing completed");
        assertThat(completedJob.completedAt()).isNotBlank();

        Document document = findDocument(file);
        assertThat(document.processingStatus()).isEqualTo(DocumentProcessingStatus.INDEXED);
        assertThat(document.chunkCount()).isPositive();
    }

    @Test
    void unsupportedDocFileIsSkippedWithoutFailingJob() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("legacy.doc"), "Unsupported Word content");
        IngestionJob job = createPendingJob("job-doc");

        documentIngestionRunner.runIngestion(job.jobId(), documentSource);

        IngestionJob completedJob = ingestionJobRepository.findByJobId(job.jobId()).orElseThrow();
        assertThat(completedJob.totalFiles()).isEqualTo(1);
        assertThat(completedJob.processedFiles()).isEqualTo(1);
        assertThat(completedJob.successfulFiles()).isZero();
        assertThat(completedJob.failedFiles()).isZero();
        assertThat(completedJob.skippedFiles()).isEqualTo(1);
        assertThat(completedJob.status()).isEqualTo(IngestionJobStatus.COMPLETED);
        assertThat(completedJob.currentStep()).isEqualTo("Processing completed");

        Document document = findDocument(file);
        assertThat(document.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
    }

    private IngestionJob createPendingJob(String jobId) {
        return ingestionJobRepository.create(new IngestionJob(
                null,
                jobId,
                WATCHED_FOLDER_ID,
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
    }

    private Document findDocument(Path file) {
        return documentRepository.findByWatchedFolderIdAndFilePath(
                        WATCHED_FOLDER_ID,
                        file.toAbsolutePath().toString()
                )
                .orElseThrow();
    }

    private void createTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE documents (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    document_uuid TEXT NOT NULL UNIQUE,
                    watched_folder_id INTEGER NOT NULL,
                    file_path TEXT NOT NULL,
                    file_name TEXT NOT NULL,
                    file_type TEXT,
                    file_size INTEGER,
                    last_modified_at TEXT,
                    content_hash TEXT,
                    processing_status TEXT NOT NULL DEFAULT 'DISCOVERED',
                    chunk_count INTEGER NOT NULL DEFAULT 0,
                    last_processed_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    UNIQUE(watched_folder_id, file_path)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE processing_jobs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    job_id TEXT NOT NULL UNIQUE,
                    watched_folder_id INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    total_files INTEGER NOT NULL DEFAULT 0,
                    processed_files INTEGER NOT NULL DEFAULT 0,
                    successful_files INTEGER NOT NULL DEFAULT 0,
                    failed_files INTEGER NOT NULL DEFAULT 0,
                    skipped_files INTEGER NOT NULL DEFAULT 0,
                    current_file TEXT,
                    current_step TEXT,
                    current_chunk INTEGER NOT NULL DEFAULT 0,
                    total_chunks_for_current_file INTEGER NOT NULL DEFAULT 0,
                    started_at TEXT,
                    completed_at TEXT,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """);
    }
}
