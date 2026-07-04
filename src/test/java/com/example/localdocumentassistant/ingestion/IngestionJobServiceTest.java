package com.example.localdocumentassistant.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.localdocumentassistant.api.LocalDocumentAssistantController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.StartProcessingJobResponse;
import com.example.localdocumentassistant.documentsource.DocumentSource;
import com.example.localdocumentassistant.documentsource.DocumentSourceRepository;

@ExtendWith(MockitoExtension.class)
class IngestionJobServiceTest {

    @Mock
    private DocumentSourceRepository documentSourceRepository;

    @Mock
    private IngestionJobRepository ingestionJobRepository;

    @Mock
    private DocumentIngestionRunner documentIngestionRunner;

    private IngestionJobService ingestionJobService;

    @BeforeEach
    void setUp() {
        ingestionJobService = new IngestionJobService(
                documentSourceRepository,
                ingestionJobRepository,
                documentIngestionRunner
        );
    }

    @Test
    void startProcessingJobCreatesSourceAndJobAndStartsProcessor() {
        DocumentSource savedSource = configuredSource();
        when(documentSourceRepository.create(any(DocumentSource.class))).thenReturn(savedSource);
        when(ingestionJobRepository.create(any(IngestionJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartProcessingJobResponse response = ingestionJobService.startProcessingJob(
                new StartProcessingJobRequest("/Users/example/Documents", true)
        );

        ArgumentCaptor<DocumentSource> sourceCaptor = ArgumentCaptor.forClass(DocumentSource.class);
        verify(documentSourceRepository).create(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().path()).isEqualTo("/Users/example/Documents");
        assertThat(sourceCaptor.getValue().includeSubfolders()).isTrue();

        ArgumentCaptor<IngestionJob> jobCaptor = ArgumentCaptor.forClass(IngestionJob.class);
        verify(ingestionJobRepository).create(jobCaptor.capture());
        IngestionJob createdJob = jobCaptor.getValue();
        assertThat(createdJob.jobId()).startsWith("job-");
        assertThat(createdJob.watchedFolderId()).isEqualTo(savedSource.id());
        assertThat(createdJob.status()).isEqualTo(IngestionJobStatus.PENDING);
        assertThat(createdJob.totalFiles()).isZero();
        assertThat(createdJob.processedFiles()).isZero();
        assertThat(createdJob.currentStep()).isEqualTo("Waiting to start processing");

        verify(documentIngestionRunner).startIngestion(createdJob.jobId(), savedSource);
        assertThat(response.jobId()).isEqualTo(createdJob.jobId());
        assertThat(response.pollUrl()).isEqualTo("/api/processing-jobs/" + createdJob.jobId());
        assertThat(response.message()).doesNotContainIgnoringCase("mock");
    }

    @Test
    void startProcessingJobForExistingSourceStartsJobWhenSourceExists() {
        DocumentSource source = configuredSource();
        when(documentSourceRepository.findById(source.id())).thenReturn(Optional.of(source));
        when(ingestionJobRepository.create(any(IngestionJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<StartProcessingJobResponse> response =
                ingestionJobService.startProcessingJobForExistingSource(source.id());

        assertThat(response).isPresent();
        verify(ingestionJobRepository).create(any(IngestionJob.class));
        verify(documentIngestionRunner).startIngestion(response.orElseThrow().jobId(), source);
    }

    @Test
    void startProcessingJobForExistingSourceReturnsEmptyWhenSourceDoesNotExist() {
        when(documentSourceRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<StartProcessingJobResponse> response =
                ingestionJobService.startProcessingJobForExistingSource(99L);

        assertThat(response).isEmpty();
        verify(ingestionJobRepository, never()).create(any(IngestionJob.class));
        verifyNoInteractions(documentIngestionRunner);
    }

    @Test
    void pollProcessingJobStatusMapsStoredJobAndCalculatesProgress() {
        String startedAt = Instant.parse("2026-07-04T09:00:00Z").toString();
        IngestionJob storedJob = new IngestionJob(
                12L,
                "job-test-001",
                7L,
                IngestionJobStatus.RUNNING,
                20,
                5,
                4,
                1,
                0,
                null,
                "Hashing discovered documents",
                0,
                0,
                startedAt,
                null
        );
        when(ingestionJobRepository.findByJobId(storedJob.jobId())).thenReturn(Optional.of(storedJob));

        ProcessingJobResponse response = ingestionJobService
                .pollProcessingJobStatus(storedJob.jobId())
                .orElseThrow();

        assertThat(response.id()).isEqualTo(storedJob.jobId());
        assertThat(response.status()).isEqualTo("RUNNING");
        assertThat(response.progressPercent()).isEqualTo(25);
        assertThat(response.processedFiles()).isEqualTo(5);
        assertThat(response.totalFiles()).isEqualTo(20);
        assertThat(response.currentStep()).isEqualTo("Hashing discovered documents");
        assertThat(response.startedAt()).isEqualTo(Instant.parse(startedAt));
    }

    private DocumentSource configuredSource() {
        return new DocumentSource(7L, "/Users/example/Documents", true, "CONFIGURED");
    }
}
