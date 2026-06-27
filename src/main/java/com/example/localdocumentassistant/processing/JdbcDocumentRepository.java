package com.example.localdocumentassistant.processing;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDocumentRepository implements DocumentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDocumentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Document create(Document document) {
        String now = Instant.now().toString();
        String documentUuid = hasText(document.documentUuid())
                ? document.documentUuid()
                : UUID.randomUUID().toString();
        DocumentProcessingStatus processingStatus = document.processingStatus() == null
                ? DocumentProcessingStatus.DISCOVERED
                : document.processingStatus();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            INSERT INTO documents (
                                document_uuid,
                                watched_folder_id,
                                file_path,
                                file_name,
                                file_type,
                                file_size,
                                last_modified_at,
                                content_hash,
                                processing_status,
                                chunk_count,
                                last_processed_at,
                                created_at,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    new String[]{"id"}
            );
            statement.setString(1, documentUuid);
            statement.setLong(2, document.watchedFolderId());
            statement.setString(3, document.filePath());
            statement.setString(4, document.fileName());
            statement.setString(5, document.fileType());
            setNullableLong(statement, 6, document.fileSize());
            statement.setString(7, document.lastModifiedAt());
            statement.setString(8, document.contentHash());
            statement.setString(9, processingStatus.name());
            statement.setInt(10, document.chunkCount());
            statement.setString(11, document.lastProcessedAt());
            statement.setString(12, now);
            statement.setString(13, now);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return a documents id.");
        }

        return withIdUuidAndStatus(document, generatedId.longValue(), documentUuid, processingStatus);
    }

    @Override
    public Optional<Document> findByDocumentUuid(String documentUuid) {
        List<Document> documents = jdbcTemplate.query(
                selectDocumentsSql() + " WHERE document_uuid = ?",
                this::mapDocument,
                documentUuid
        );

        return documents.stream().findFirst();
    }

    @Override
    public List<Document> findByWatchedFolderId(Long watchedFolderId) {
        return jdbcTemplate.query(
                selectDocumentsSql() + " WHERE watched_folder_id = ? ORDER BY file_path",
                this::mapDocument,
                watchedFolderId
        );
    }

    @Override
    public List<Document> findByWatchedFolderId(Long watchedFolderId, int limit, int offset) {
        return jdbcTemplate.query(
                selectDocumentsSql() + " WHERE watched_folder_id = ? ORDER BY file_path LIMIT ? OFFSET ?",
                this::mapDocument,
                watchedFolderId,
                limit,
                offset
        );
    }

    @Override
    public long countByWatchedFolderId(Long watchedFolderId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE watched_folder_id = ?",
                Long.class,
                watchedFolderId
        );
        return count == null ? 0 : count;
    }

    @Override
    public Optional<Document> findByWatchedFolderIdAndFilePath(Long watchedFolderId, String filePath) {
        List<Document> documents = jdbcTemplate.query(
                selectDocumentsSql() + " WHERE watched_folder_id = ? AND file_path = ?",
                this::mapDocument,
                watchedFolderId,
                filePath
        );

        return documents.stream().findFirst();
    }

    @Override
    public Document update(Document document) {
        int rowsUpdated = jdbcTemplate.update(
                """
                        UPDATE documents
                        SET file_path = ?,
                            file_name = ?,
                            file_type = ?,
                            file_size = ?,
                            last_modified_at = ?,
                            content_hash = ?,
                            processing_status = ?,
                            chunk_count = ?,
                            last_processed_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                document.filePath(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.lastModifiedAt(),
                document.contentHash(),
                document.processingStatus().name(),
                document.chunkCount(),
                document.lastProcessedAt(),
                Instant.now().toString(),
                document.id()
        );
        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("No document found for id: " + document.id());
        }

        return document;
    }

    @Override
    public Document updateProcessingStatus(Long id, DocumentProcessingStatus status) {
        int rowsUpdated = jdbcTemplate.update(
                """
                        UPDATE documents
                        SET processing_status = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                status.name(),
                Instant.now().toString(),
                id
        );
        if (rowsUpdated == 0) {
            throw new IllegalArgumentException("No document found for id: " + id);
        }

        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No document found for id: " + id));
    }

    private Optional<Document> findById(Long id) {
        List<Document> documents = jdbcTemplate.query(
                selectDocumentsSql() + " WHERE id = ?",
                this::mapDocument,
                id
        );

        return documents.stream().findFirst();
    }

    private String selectDocumentsSql() {
        return """
                SELECT id,
                       document_uuid,
                       watched_folder_id,
                       file_path,
                       file_name,
                       file_type,
                       file_size,
                       last_modified_at,
                       content_hash,
                       processing_status,
                       chunk_count,
                       last_processed_at
                FROM documents
                """;
    }

    private Document mapDocument(ResultSet rs, int rowNum) throws SQLException {
        return new Document(
                rs.getLong("id"),
                rs.getString("document_uuid"),
                rs.getLong("watched_folder_id"),
                rs.getString("file_path"),
                rs.getString("file_name"),
                rs.getString("file_type"),
                nullableLong(rs, "file_size"),
                rs.getString("last_modified_at"),
                rs.getString("content_hash"),
                DocumentProcessingStatus.valueOf(rs.getString("processing_status")),
                rs.getInt("chunk_count"),
                rs.getString("last_processed_at")
        );
    }

    private Document withIdUuidAndStatus(
            Document document,
            long id,
            String documentUuid,
            DocumentProcessingStatus processingStatus
    ) {
        return new Document(
                id,
                documentUuid,
                document.watchedFolderId(),
                document.filePath(),
                document.fileName(),
                document.fileType(),
                document.fileSize(),
                document.lastModifiedAt(),
                document.contentHash(),
                processingStatus,
                document.chunkCount(),
                document.lastProcessedAt()
        );
    }

    private void setNullableLong(java.sql.PreparedStatement statement, int index, Long value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.INTEGER);
            return;
        }

        statement.setLong(index, value);
    }

    private Long nullableLong(ResultSet rs, String columnName) throws SQLException {
        long value = rs.getLong(columnName);
        return rs.wasNull() ? null : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
