package com.example.localdocumentassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import com.example.localdocumentassistant.documentcatalog.Document;
import com.example.localdocumentassistant.documentcatalog.DocumentProcessingStatus;
import com.example.localdocumentassistant.documentcatalog.DocumentRepository;
import com.example.localdocumentassistant.indexing.DocumentIndexingService;
import com.example.localdocumentassistant.indexing.DocumentVectorStore;
import com.example.localdocumentassistant.persistence.JdbcDocumentRepository;

class DocumentTextProcessingServiceTest {

    private static final long WATCHED_FOLDER_ID = 42L;

    @TempDir
    Path tempDirectory;

    private DocumentRepository documentRepository;
    private DocumentTextProcessingService documentTextProcessingService;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("text-processing-test.sqlite"));

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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

        documentRepository = new JdbcDocumentRepository(jdbcTemplate);
        DocumentIndexingService indexingService = new DocumentIndexingService(
                documentRepository,
                text -> List.of(0.1, 0.2),
                mock(DocumentVectorStore.class)
        );
        documentTextProcessingService = new DocumentTextProcessingService(
                documentRepository,
                List.of(new PlainTextExtractor()),
                new FixedSizeTextChunker(5),
                indexingService
        );
    }

    @Test
    void needsProcessingTxtDocumentBecomesIndexed() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("notes.txt"), "Hello world!");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "txt"));

        Document documentToProcess = documentTextProcessingService
                .findDocumentsNeedingProcessing(WATCHED_FOLDER_ID)
                .get(0);
        DocumentProcessingOutcome outcome = documentTextProcessingService.processDocument(documentToProcess);

        Document processedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(processedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.INDEXED);
        assertThat(processedDocument.chunkCount()).isEqualTo(3);
        assertThat(processedDocument.lastProcessedAt()).isNotBlank();
        assertThat(outcome).isEqualTo(DocumentProcessingOutcome.SUCCESSFUL);
    }

    @Test
    void needsProcessingDocxDocumentBecomesIndexedWithTikaExtractor() throws Exception {
        Path file = createDocx("This Word document is about warehouse forecasting.");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "docx"));
        DocumentTextProcessingService service = new DocumentTextProcessingService(
                documentRepository,
                List.of(new PlainTextExtractor(), new TikaDocumentTextExtractor()),
                new FixedSizeTextChunker(20),
                indexingService()
        );

        DocumentProcessingOutcome outcome = service.processDocument(savedDocument);

        Document processedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(outcome).isEqualTo(DocumentProcessingOutcome.SUCCESSFUL);
        assertThat(processedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.INDEXED);
        assertThat(processedDocument.chunkCount()).isPositive();
        assertThat(processedDocument.lastProcessedAt()).isNotBlank();
    }

    @Test
    void needsProcessingDocDocumentRemainsUnchangedWithoutExtractor() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("legacy.doc"), "Not parsed in this slice");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "doc"));

        Document documentToProcess = documentTextProcessingService
                .findDocumentsNeedingProcessing(WATCHED_FOLDER_ID)
                .get(0);
        DocumentProcessingOutcome outcome = documentTextProcessingService.processDocument(documentToProcess);

        Document unprocessedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(unprocessedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
        assertThat(unprocessedDocument.chunkCount()).isZero();
        assertThat(unprocessedDocument.lastProcessedAt()).isNull();
        assertThat(outcome).isEqualTo(DocumentProcessingOutcome.SKIPPED);
    }

    @Test
    void indexingFailureLeavesTxtDocumentNeedingProcessing() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("retry.txt"), "Retry this document");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "txt"));
        DocumentIndexingService failingIndexingService = mock(DocumentIndexingService.class);
        doThrow(new IllegalStateException("Vector service unavailable"))
                .when(failingIndexingService).index(any(Document.class), anyList());
        DocumentTextProcessingService service = new DocumentTextProcessingService(
                documentRepository,
                List.of(new PlainTextExtractor()),
                new FixedSizeTextChunker(5),
                failingIndexingService
        );

        DocumentProcessingOutcome outcome = service.processDocument(savedDocument);

        Document unprocessedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(outcome).isEqualTo(DocumentProcessingOutcome.FAILED);
        assertThat(unprocessedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
        assertThat(unprocessedDocument.chunkCount()).isZero();
        assertThat(unprocessedDocument.lastProcessedAt()).isNull();
    }

    @Test
    void blankExtractedTextLeavesDocumentNeedingProcessing() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("blank.docx"), "placeholder");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "docx"));
        DocumentTextExtractor blankExtractor = new DocumentTextExtractor() {
            @Override
            public boolean supports(String fileType) {
                return "docx".equals(fileType);
            }

            @Override
            public String extract(Path path) {
                return "   \n";
            }
        };
        DocumentTextProcessingService service = new DocumentTextProcessingService(
                documentRepository,
                List.of(blankExtractor),
                new FixedSizeTextChunker(5),
                mock(DocumentIndexingService.class)
        );

        DocumentProcessingOutcome outcome = service.processDocument(savedDocument);

        Document unprocessedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(outcome).isEqualTo(DocumentProcessingOutcome.FAILED);
        assertThat(unprocessedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
        assertThat(unprocessedDocument.chunkCount()).isZero();
        assertThat(unprocessedDocument.lastProcessedAt()).isNull();
    }

    private Document needsProcessingDocument(Path file, String fileType) throws Exception {
        return new Document(
                null,
                null,
                WATCHED_FOLDER_ID,
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                fileType,
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant().toString(),
                "existing-content-hash",
                DocumentProcessingStatus.NEEDS_PROCESSING,
                0,
                null
        );
    }

    private DocumentIndexingService indexingService() {
        return new DocumentIndexingService(
                documentRepository,
                text -> List.of(0.1, 0.2),
                mock(DocumentVectorStore.class)
        );
    }

    private Path createDocx(String text) throws Exception {
        Path path = tempDirectory.resolve("warehouse.docx");
        try (XWPFDocument document = new XWPFDocument();
                OutputStream output = Files.newOutputStream(path)) {
            document.createParagraph().createRun().setText(text);
            document.write(output);
        }
        return path;
    }
}
