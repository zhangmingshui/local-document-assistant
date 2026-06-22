package com.example.localdocumentassistant.processing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDocumentSourceRepository implements DocumentSourceRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentSourceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public DocumentSource create(DocumentSource source) {
        if (findByPath(source.path()).isPresent()) {
            throw new DuplicateDocumentSourceException("This directory is already configured. Choose another directory.");
        }

        String now = Instant.now().toString();
        jdbcTemplate.update(
                """
                        INSERT INTO watched_folders (
                            path,
                            enabled,
                            include_subfolders,
                            status,
                            created_at,
                            updated_at
                        )
                        VALUES (?, 1, ?, ?, ?, ?)
                        """,
                source.path(),
                source.includeSubfolders() ? 1 : 0,
                source.status(),
                now,
                now
        );

        return source;
    }

    @Override
    public Optional<DocumentSource> findByPath(String path) {
        List<DocumentSource> sources = jdbcTemplate.query(
                """
                        SELECT path, include_subfolders, status
                        FROM watched_folders
                        WHERE path = ?
                        """,
                (rs, rowNum) -> mapDocumentSource(rs.getString("path"), rs.getInt("include_subfolders"), rs.getString("status")),
                path
        );

        return sources.stream().findFirst();
    }

    @Override
    public List<DocumentSource> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT path, include_subfolders, status
                        FROM watched_folders
                        ORDER BY path
                        """,
                (rs, rowNum) -> mapDocumentSource(rs.getString("path"), rs.getInt("include_subfolders"), rs.getString("status"))
        );
    }

    private DocumentSource mapDocumentSource(String path, int includeSubfolders, String status) {
        return new DocumentSource(path, includeSubfolders == 1, status);
    }
}
