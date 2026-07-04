package com.example.localdocumentassistant.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SqliteDatabaseInitializer implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteDatabaseInitializer.class);
    private static final String WATCHED_FOLDERS_TABLE = """
            CREATE TABLE IF NOT EXISTS watched_folders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                path TEXT NOT NULL UNIQUE,
                enabled INTEGER NOT NULL DEFAULT 1,
                include_subfolders INTEGER NOT NULL DEFAULT 1,
                status TEXT NOT NULL DEFAULT 'CONFIGURED',
                last_scanned_at TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            )
            """;
    private static final String PROCESSING_JOBS_TABLE = """
            CREATE TABLE IF NOT EXISTS processing_jobs (
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
                updated_at TEXT NOT NULL,
                FOREIGN KEY (watched_folder_id) REFERENCES watched_folders(id)
            )
            """;
    private static final String DOCUMENTS_TABLE = """
            CREATE TABLE IF NOT EXISTS documents (
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
                UNIQUE(watched_folder_id, file_path),
                FOREIGN KEY (watched_folder_id) REFERENCES watched_folders(id)
            )
            """;
    private static final String DOCUMENTS_WATCHED_FOLDER_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_documents_watched_folder_id
            ON documents(watched_folder_id)
            """;
    private static final String DOCUMENTS_DOCUMENT_UUID_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_documents_document_uuid
            ON documents(document_uuid)
            """;
    private static final String DOCUMENTS_CONTENT_HASH_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_documents_content_hash
            ON documents(content_hash)
            """;
    private static final String PROCESSING_ERRORS_TABLE = """
            CREATE TABLE IF NOT EXISTS processing_errors (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                processing_job_id INTEGER NOT NULL,
                document_id INTEGER,
                file_path TEXT,
                error_type TEXT,
                error_message TEXT NOT NULL,
                created_at TEXT NOT NULL,
                FOREIGN KEY (processing_job_id) REFERENCES processing_jobs(id),
                FOREIGN KEY (document_id) REFERENCES documents(id)
            )
            """;
    private static final String PROCESSING_ERRORS_JOB_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_errors_processing_job_id
            ON processing_errors(processing_job_id)
            """;
    private static final String PROCESSING_ERRORS_DOCUMENT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_errors_document_id
            ON processing_errors(document_id)
            """;
    private static final String PROCESSING_ERRORS_CREATED_AT_INDEX = """
            CREATE INDEX IF NOT EXISTS idx_processing_errors_created_at
            ON processing_errors(created_at)
            """;

    private final JdbcTemplate jdbcTemplate;

    public SqliteDatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(Path.of("data"));

        String sqliteVersion = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);
        LOGGER.info("Connected to SQLite version {} using ./data/local-document-assistant.sqlite", sqliteVersion);

        jdbcTemplate.execute(WATCHED_FOLDERS_TABLE);
        jdbcTemplate.execute(PROCESSING_JOBS_TABLE);
        jdbcTemplate.execute(DOCUMENTS_TABLE);
        jdbcTemplate.execute(DOCUMENTS_WATCHED_FOLDER_INDEX);
        jdbcTemplate.execute(DOCUMENTS_DOCUMENT_UUID_INDEX);
        jdbcTemplate.execute(DOCUMENTS_CONTENT_HASH_INDEX);
        jdbcTemplate.execute(PROCESSING_ERRORS_TABLE);
        jdbcTemplate.execute(PROCESSING_ERRORS_JOB_INDEX);
        jdbcTemplate.execute(PROCESSING_ERRORS_DOCUMENT_INDEX);
        jdbcTemplate.execute(PROCESSING_ERRORS_CREATED_AT_INDEX);

        verifyTableExists("watched_folders");
        verifyTableExists("processing_jobs");
        verifyTableExists("documents");
        verifyTableExists("processing_errors");
    }

    private void verifyTableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = ?",
                Integer.class,
                tableName
        );
        if (tableCount == null || tableCount == 0) {
            throw new IllegalStateException("SQLite table " + tableName + " was not created.");
        }

        LOGGER.info("SQLite table {} exists.", tableName);
    }
}
