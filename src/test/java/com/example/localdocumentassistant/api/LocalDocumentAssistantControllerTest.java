package com.example.localdocumentassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.localdocumentassistant.api.LocalDocumentAssistantController.ErrorResponse;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.QuestionRequest;
import com.example.localdocumentassistant.documentcatalog.DocumentQueryService;
import com.example.localdocumentassistant.documentsource.DocumentSourceService;
import com.example.localdocumentassistant.indexing.DocumentSearchService;
import com.example.localdocumentassistant.ingestion.IngestionJobService;
import com.example.localdocumentassistant.questionanswering.QuestionAnsweringService;

@ExtendWith(MockitoExtension.class)
class LocalDocumentAssistantControllerTest {

    @Mock
    private DocumentSourceService documentSourceService;

    @Mock
    private DocumentQueryService documentQueryService;

    @Mock
    private IngestionJobService ingestionJobService;

    @Mock
    private DocumentSearchService documentSearchService;

    @Mock
    private QuestionAnsweringService questionAnsweringService;

    @Test
    void blankQuestionReturnsBadRequestWithoutCallingQuestionAnsweringService() {
        LocalDocumentAssistantController controller = new LocalDocumentAssistantController(
                documentSourceService,
                documentQueryService,
                ingestionJobService,
                documentSearchService,
                questionAnsweringService
        );

        ResponseEntity<?> response = controller.askQuestion(new QuestionRequest("  "));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(new ErrorResponse("Question must not be blank."));
        verifyNoInteractions(questionAnsweringService);
    }
}
