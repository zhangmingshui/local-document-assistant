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

    private final JdbcTemplate jdbcTemplate;

    public SqliteStartupVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Files.createDirectories(Path.of("data"));

        String sqliteVersion = jdbcTemplate.queryForObject("SELECT sqlite_version()", String.class);
        LOGGER.info("Connected to SQLite version {} using ./data/local-document-assistant.sqlite", sqliteVersion);
    }
}
