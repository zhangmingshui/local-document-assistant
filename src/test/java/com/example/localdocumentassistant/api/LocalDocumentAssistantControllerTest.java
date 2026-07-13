package com.example.localdocumentassistant.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.example.localdocumentassistant.api.LocalDocumentAssistantController.ErrorResponse;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.QuestionRequest;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.SearchRequest;
import com.example.localdocumentassistant.api.LocalDocumentAssistantController.SearchResponse;
import com.example.localdocumentassistant.documentcatalog.DocumentQueryService;
import com.example.localdocumentassistant.documentsource.DocumentSourceService;
import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
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

    @Test
    void searchResponseIncludesDistanceAndCalculatedRelevance() {
        LocalDocumentAssistantController controller = new LocalDocumentAssistantController(
                documentSourceService,
                documentQueryService,
                ingestionJobService,
                documentSearchService,
                questionAnsweringService
        );
        when(documentSearchService.search("warehouse", 5)).thenReturn(List.of(new DocumentSearchMatch(
                "matching chunk",
                "warehouse.txt",
                "/documents/warehouse.txt",
                0,
                42L,
                "document-42",
                0.25
        )));

        ResponseEntity<?> response = controller.search(new SearchRequest("warehouse", 5));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        SearchResponse body = (SearchResponse) response.getBody();
        assertThat(body.results()).hasSize(1);
        assertThat(body.results().get(0).distance()).isEqualTo(0.25);
        assertThat(body.results().get(0).relevance()).isEqualTo(0.8);
    }
}
