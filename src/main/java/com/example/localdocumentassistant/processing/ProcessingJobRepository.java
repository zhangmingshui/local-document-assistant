package com.example.localdocumentassistant.processing;

import java.util.Optional;

public interface ProcessingJobRepository {

    ProcessingJob create(ProcessingJob job);

    Optional<ProcessingJob> findByJobId(String jobId);

    ProcessingJob update(ProcessingJob job);
}
