package com.example.localdocumentassistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import com.example.localdocumentassistant.infrastructure.sqlite.JdbcDocumentRepository;

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
        documentTextProcessingService = new DocumentTextProcessingService(
                documentRepository,
                List.of(new PlainTextDocumentTextExtractor()),
                new SimpleCharacterTextChunker(5)
        );
    }

    @Test
    void needsProcessingTxtDocumentBecomesChunked() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("notes.txt"), "Hello world!");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "txt"));

        documentTextProcessingService.processDocuments(WATCHED_FOLDER_ID);

        Document processedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
        assertThat(processedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.CHUNKED);
        assertThat(processedDocument.chunkCount()).isEqualTo(3);
        assertThat(processedDocument.lastProcessedAt()).isNotBlank();
    }

    @Test
    void needsProcessingDocDocumentRemainsUnchangedWithoutExtractor() throws Exception {
        Path file = Files.writeString(tempDirectory.resolve("legacy.doc"), "Not parsed in this slice");
        Document savedDocument = documentRepository.create(needsProcessingDocument(file, "doc"));

        documentTextProcessingService.processDocuments(WATCHED_FOLDER_ID);

        Document unprocessedDocument = documentRepository.findByDocumentUuid(savedDocument.documentUuid())
                .orElseThrow();
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
}
