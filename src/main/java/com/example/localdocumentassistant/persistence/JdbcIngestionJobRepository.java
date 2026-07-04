package com.example.localdocumentassistant.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import com.example.localdocumentassistant.ingestion.IngestionJob;
import com.example.localdocumentassistant.ingestion.IngestionJobRepository;
import com.example.localdocumentassistant.ingestion.IngestionJobStatus;

@Repository
public class JdbcIngestionJobRepository implements IngestionJobRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcIngestionJobRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public IngestionJob create(IngestionJob job) {
        String now = Instant.now().toString();
        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement(
                    """
                            INSERT INTO processing_jobs (
                                job_id,
                                watched_folder_id,
                                status,
                                total_files,
                                processed_files,
                                successful_files,
                                failed_files,
                                skipped_files,
                                current_file,
                                current_step,
                                current_chunk,
                                total_chunks_for_current_file,
                                started_at,
                                completed_at,
                                created_at,
                                updated_at
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    new String[]{"id"}
            );
            statement.setString(1, job.jobId());
            statement.setLong(2, job.watchedFolderId());
            statement.setString(3, job.status().name());
            statement.setInt(4, job.totalFiles());
            statement.setInt(5, job.processedFiles());
            statement.setInt(6, job.successfulFiles());
            statement.setInt(7, job.failedFiles());
            statement.setInt(8, job.skippedFiles());
            statement.setString(9, job.currentFile());
            statement.setString(10, job.currentStep());
            statement.setInt(11, job.currentChunk());
            statement.setInt(12, job.totalChunksForCurrentFile());
            statement.setString(13, job.startedAt());
            statement.setString(14, job.completedAt());
            statement.setString(15, now);
            statement.setString(16, now);
            return statement;
        }, keyHolder);

        Number generatedId = keyHolder.getKey();
        if (generatedId == null) {
            throw new IllegalStateException("SQLite did not return a processing_jobs id.");
        }

        return withId(job, generatedId.longValue());
    }

    @Override
    public Optional<IngestionJob> findByJobId(String jobId) {
        List<IngestionJob> jobs = jdbcTemplate.query(
                """
                        SELECT id,
                               job_id,
                               watched_folder_id,
                               status,
                               total_files,
                               processed_files,
                               successful_files,
                               failed_files,
                               skipped_files,
                               current_file,
                               current_step,
                               current_chunk,
                               total_chunks_for_current_file,
                               started_at,
                               completed_at
                        FROM processing_jobs
                        WHERE job_id = ?
                        """,
                (rs, rowNum) -> new IngestionJob(
                        rs.getLong("id"),
                        rs.getString("job_id"),
                        rs.getLong("watched_folder_id"),
                        IngestionJobStatus.valueOf(rs.getString("status")),
                        rs.getInt("total_files"),
                        rs.getInt("processed_files"),
                        rs.getInt("successful_files"),
                        rs.getInt("failed_files"),
                        rs.getInt("skipped_files"),
                        rs.getString("current_file"),
                        rs.getString("current_step"),
                        rs.getInt("current_chunk"),
                        rs.getInt("total_chunks_for_current_file"),
                        rs.getString("started_at"),
                        rs.getString("completed_at")
                ),
                jobId
        );

        return jobs.stream().findFirst();
    }

    @Override
    public IngestionJob update(IngestionJob job) {
        jdbcTemplate.update(
                """
                        UPDATE processing_jobs
                        SET status = ?,
                            total_files = ?,
                            processed_files = ?,
                            successful_files = ?,
                            failed_files = ?,
                            skipped_files = ?,
                            current_file = ?,
                            current_step = ?,
                            current_chunk = ?,
                            total_chunks_for_current_file = ?,
                            started_at = ?,
                            completed_at = ?,
                            updated_at = ?
                        WHERE id = ?
                        """,
                job.status().name(),
                job.totalFiles(),
                job.processedFiles(),
                job.successfulFiles(),
                job.failedFiles(),
                job.skippedFiles(),
                job.currentFile(),
                job.currentStep(),
                job.currentChunk(),
                job.totalChunksForCurrentFile(),
                job.startedAt(),
                job.completedAt(),
                Instant.now().toString(),
                job.id()
        );

        return job;
    }

    private IngestionJob withId(IngestionJob job, long id) {
        return new IngestionJob(
                id,
                job.jobId(),
                job.watchedFolderId(),
                job.status(),
                job.totalFiles(),
                job.processedFiles(),
                job.successfulFiles(),
                job.failedFiles(),
                job.skippedFiles(),
                job.currentFile(),
                job.currentStep(),
                job.currentChunk(),
                job.totalChunksForCurrentFile(),
                job.startedAt(),
                job.completedAt()
        );
    }
}
