package com.example.localdocumentassistant.processing;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Repository;

@Repository
public class InMemoryProcessingJobRepository {

    private final Map<String, ProcessingJobService.MockProcessingJob> processingJobs = new ConcurrentHashMap<>();

    public void save(ProcessingJobService.MockProcessingJob job) {
        processingJobs.put(job.id(), job);
    }

    public Optional<ProcessingJobService.MockProcessingJob> findById(String jobId) {
        return Optional.ofNullable(processingJobs.get(jobId));
    }
}
