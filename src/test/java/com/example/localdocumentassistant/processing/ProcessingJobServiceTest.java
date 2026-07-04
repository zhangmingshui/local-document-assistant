package com.example.localdocumentassistant.processing;

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

import com.example.localdocumentassistant.api.MockApiController.ProcessingJobResponse;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobRequest;
import com.example.localdocumentassistant.api.MockApiController.StartProcessingJobResponse;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private DocumentSourceRepository documentSourceRepository;

    @Mock
    private ProcessingJobRepository processingJobRepository;

    @Mock
    private MockDocumentProcessingService mockDocumentProcessingService;

    private ProcessingJobService processingJobService;

    @BeforeEach
    void setUp() {
        processingJobService = new ProcessingJobService(
                documentSourceRepository,
                processingJobRepository,
                mockDocumentProcessingService
        );
    }

    @Test
    void startProcessingJobCreatesSourceAndJobAndStartsProcessor() {
        DocumentSource savedSource = configuredSource();
        when(documentSourceRepository.create(any(DocumentSource.class))).thenReturn(savedSource);
        when(processingJobRepository.create(any(ProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        StartProcessingJobResponse response = processingJobService.startProcessingJob(
                new StartProcessingJobRequest("/Users/example/Documents", true)
        );

        ArgumentCaptor<DocumentSource> sourceCaptor = ArgumentCaptor.forClass(DocumentSource.class);
        verify(documentSourceRepository).create(sourceCaptor.capture());
        assertThat(sourceCaptor.getValue().path()).isEqualTo("/Users/example/Documents");
        assertThat(sourceCaptor.getValue().includeSubfolders()).isTrue();

        ArgumentCaptor<ProcessingJob> jobCaptor = ArgumentCaptor.forClass(ProcessingJob.class);
        verify(processingJobRepository).create(jobCaptor.capture());
        ProcessingJob createdJob = jobCaptor.getValue();
        assertThat(createdJob.jobId()).startsWith("job-");
        assertThat(createdJob.watchedFolderId()).isEqualTo(savedSource.id());
        assertThat(createdJob.status()).isEqualTo(ProcessingJobStatus.PENDING);

        verify(mockDocumentProcessingService).startMockProcessing(createdJob.jobId(), savedSource);
        assertThat(response.jobId()).isEqualTo(createdJob.jobId());
        assertThat(response.pollUrl()).isEqualTo("/api/processing-jobs/" + createdJob.jobId());
    }

    @Test
    void startProcessingJobForExistingSourceStartsJobWhenSourceExists() {
        DocumentSource source = configuredSource();
        when(documentSourceRepository.findById(source.id())).thenReturn(Optional.of(source));
        when(processingJobRepository.create(any(ProcessingJob.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<StartProcessingJobResponse> response =
                processingJobService.startProcessingJobForExistingSource(source.id());

        assertThat(response).isPresent();
        verify(processingJobRepository).create(any(ProcessingJob.class));
        verify(mockDocumentProcessingService).startMockProcessing(response.orElseThrow().jobId(), source);
    }

    @Test
    void startProcessingJobForExistingSourceReturnsEmptyWhenSourceDoesNotExist() {
        when(documentSourceRepository.findById(99L)).thenReturn(Optional.empty());

        Optional<StartProcessingJobResponse> response =
                processingJobService.startProcessingJobForExistingSource(99L);

        assertThat(response).isEmpty();
        verify(processingJobRepository, never()).create(any(ProcessingJob.class));
        verifyNoInteractions(mockDocumentProcessingService);
    }

    @Test
    void pollProcessingJobStatusMapsStoredJobAndCalculatesProgress() {
        String startedAt = Instant.parse("2026-07-04T09:00:00Z").toString();
        ProcessingJob storedJob = new ProcessingJob(
                12L,
                "job-test-001",
                7L,
                ProcessingJobStatus.RUNNING,
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
        when(processingJobRepository.findByJobId(storedJob.jobId())).thenReturn(Optional.of(storedJob));

        ProcessingJobResponse response = processingJobService
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
