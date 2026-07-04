package com.example.localdocumentassistant.ingestion;

import java.util.Optional;

public interface IngestionJobRepository {

    IngestionJob create(IngestionJob job);

    Optional<IngestionJob> findByJobId(String jobId);

    IngestionJob update(IngestionJob job);
}
