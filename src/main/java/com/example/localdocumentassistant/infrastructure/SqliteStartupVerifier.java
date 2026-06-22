package com.example.localdocumentassistant.infrastructure;

import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SqliteStartupVerifier implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(SqliteStartupVerifier.class);
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

    private final JdbcTemplate jdbcTemplate;

    public SqliteStartupVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(Path.of("data"));

        String sqliteVersion = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);
        LOGGER.info("Connected to SQLite version {} using ./data/local-document-assistant.sqlite", sqliteVersion);

        jdbcTemplate.execute(WATCHED_FOLDERS_TABLE);

        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'watched_folders'",
                Integer.class
        );

        if (tableCount == null || tableCount == 0) {
            throw new IllegalStateException("SQLite table watched_folders was not created.");
        }

        LOGGER.info("SQLite table watched_folders exists.");
    }
}
