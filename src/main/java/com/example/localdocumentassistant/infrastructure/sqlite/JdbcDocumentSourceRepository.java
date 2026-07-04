package com.example.localdocumentassistant.infrastructure.sqlite;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.localdocumentassistant.source.DocumentSource;
import com.example.localdocumentassistant.source.DocumentSourceRepository;
import com.example.localdocumentassistant.source.DuplicateDocumentSourceException;

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
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
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
                    new String[]{"id"}
            );
            statement.setString(1, source.path());
            statement.setInt(2, source.includeSubfolders() ? 1 : 0);
            statement.setString(3, source.status());
            statement.setString(4, now);
            statement.setString(5, now);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return a watched_folders id.");
        }

        return new DocumentSource(generatedId.longValue(), source.path(), source.includeSubfolders(), source.status());
    }

    @Override
    public Optional<DocumentSource> findById(Long id) {
        List<DocumentSource> sources = jdbcTemplate.query(
                """
                        SELECT id, path, include_subfolders, status
                        FROM watched_folders
                        WHERE id = ?
                        """,
                (rs, rowNum) -> mapDocumentSource(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getInt("include_subfolders"),
                        rs.getString("status")
                ),
                id
        );

        return sources.stream().findFirst();
    }

    @Override
    public Optional<DocumentSource> findByPath(String path) {
        List<DocumentSource> sources = jdbcTemplate.query(
                """
                        SELECT id, path, include_subfolders, status
                        FROM watched_folders
                        WHERE path = ?
                        """,
                (rs, rowNum) -> mapDocumentSource(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getInt("include_subfolders"),
                        rs.getString("status")
                ),
                path
        );

        return sources.stream().findFirst();
    }

    @Override
    public List<DocumentSource> findAll() {
        return jdbcTemplate.query(
                """
                        SELECT id, path, include_subfolders, status
                        FROM watched_folders
                        ORDER BY path
                        """,
                (rs, rowNum) -> mapDocumentSource(
                        rs.getLong("id"),
                        rs.getString("path"),
                        rs.getInt("include_subfolders"),
                        rs.getString("status")
                )
        );
    }

    private DocumentSource mapDocumentSource(long id, String path, int includeSubfolders, String status) {
        return new DocumentSource(id, path, includeSubfolders == 1, status);
    }
}
