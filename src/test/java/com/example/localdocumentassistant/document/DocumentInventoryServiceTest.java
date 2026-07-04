package com.example.localdocumentassistant.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.sqlite.SQLiteDataSource;

import com.example.localdocumentassistant.infrastructure.sqlite.JdbcDocumentRepository;
import com.example.localdocumentassistant.source.DocumentSource;

class DocumentInventoryServiceTest {

    private static final long WATCHED_FOLDER_ID = 42L;

    @TempDir
    Path tempDirectory;

    private DocumentRepository documentRepository;
    private DocumentInventoryService documentInventoryService;
    private DocumentSource documentSource;

    @BeforeEach
    void setUp() {
        SQLiteDataSource dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:" + tempDirectory.resolve("inventory-test.sqlite"));

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
        documentInventoryService = new DocumentInventoryService(documentRepository);
        documentSource = new DocumentSource(WATCHED_FOLDER_ID, tempDirectory.toString(), true, "CONFIGURED");
    }

    @Test
    void persistsNewSupportedFileWithHashAndNeedsProcessingStatus() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("notes.txt"), "Local assistant notes");

        documentInventoryService.persistDiscoveredDocuments(documentSource, List.of(metadataFor(file)));

        Document savedDocument = findDocument(file);
        assertThat(savedDocument.id()).isNotNull();
        assertThat(savedDocument.documentUuid()).isNotBlank();
        assertThat(savedDocument.fileName()).isEqualTo("notes.txt");
        assertThat(savedDocument.filePath()).isEqualTo(file.toAbsolutePath().toString());
        assertThat(savedDocument.fileType()).isEqualTo("txt");
        assertThat(savedDocument.fileSize()).isEqualTo(Files.size(file));
        assertThat(savedDocument.lastModifiedAt()).isEqualTo(Files.getLastModifiedTime(file).toInstant().toString());
        assertThat(savedDocument.contentHash()).hasSize(64);
        assertThat(savedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
    }

    @Test
    void processingUnchangedFileAgainDoesNotCreateDuplicate() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("unchanged.txt"), "No changes");
        DiscoveredDocumentMetadata metadata = metadataFor(file);

        documentInventoryService.persistDiscoveredDocuments(documentSource, List.of(metadata));
        Document firstDocument = findDocument(file);

        documentInventoryService.persistDiscoveredDocuments(documentSource, List.of(metadata));
        Document secondDocument = findDocument(file);

        assertThat(documentRepository.findByWatchedFolderId(WATCHED_FOLDER_ID)).hasSize(1);
        assertThat(secondDocument.id()).isEqualTo(firstDocument.id());
        assertThat(secondDocument.documentUuid()).isEqualTo(firstDocument.documentUuid());
        assertThat(secondDocument.contentHash()).isEqualTo(firstDocument.contentHash());
    }

    @Test
    void changedFileUpdatesExistingDocumentAndHash() throws IOException {
        Path file = Files.writeString(tempDirectory.resolve("changed.txt"), "Original");
        documentInventoryService.persistDiscoveredDocuments(documentSource, List.of(metadataFor(file)));
        Document originalDocument = findDocument(file);

        Files.writeString(file, "Changed content with a different size");
        documentInventoryService.persistDiscoveredDocuments(documentSource, List.of(metadataFor(file)));
        Document changedDocument = findDocument(file);

        assertThat(documentRepository.findByWatchedFolderId(WATCHED_FOLDER_ID)).hasSize(1);
        assertThat(changedDocument.id()).isEqualTo(originalDocument.id());
        assertThat(changedDocument.documentUuid()).isEqualTo(originalDocument.documentUuid());
        assertThat(changedDocument.contentHash()).isNotEqualTo(originalDocument.contentHash());
        assertThat(changedDocument.processingStatus()).isEqualTo(DocumentProcessingStatus.NEEDS_PROCESSING);
    }

    private Document findDocument(Path file) {
        return documentRepository.findByWatchedFolderIdAndFilePath(
                        WATCHED_FOLDER_ID,
                        file.toAbsolutePath().toString()
                )
                .orElseThrow();
    }

    private DiscoveredDocumentMetadata metadataFor(Path file) throws IOException {
        return new DiscoveredDocumentMetadata(
                file,
                file.toAbsolutePath().toString(),
                file.getFileName().toString(),
                "txt",
                Files.size(file),
                Files.getLastModifiedTime(file).toInstant().toString()
        );
    }
}
