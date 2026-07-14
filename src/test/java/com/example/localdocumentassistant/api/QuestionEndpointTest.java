package com.example.localdocumentassistant.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.localdocumentassistant.documentcatalog.DocumentQueryService;
import com.example.localdocumentassistant.documentsource.DocumentSourceService;
import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
import com.example.localdocumentassistant.indexing.DocumentSearchService;
import com.example.localdocumentassistant.ingestion.IngestionJobService;
import com.example.localdocumentassistant.questionanswering.ChatModelService;
import com.example.localdocumentassistant.questionanswering.QuestionAnsweringService;

@WebMvcTest(LocalDocumentAssistantController.class)
@Import(QuestionAnsweringService.class)
class QuestionEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentSourceService documentSourceService;

    @MockitoBean
    private DocumentQueryService documentQueryService;

    @MockitoBean
    private IngestionJobService ingestionJobService;

    @MockitoBean
    private DocumentSearchService documentSearchService;

    @MockitoBean
    private ChatModelService chatModelService;

    @Test
    void questionsEndpointReturnsAnswerAndSourcesWithoutRealOllamaOrChroma() throws Exception {
        when(documentSearchService.search("Where does Pom Pom sleep?", 8))
                .thenReturn(List.of(new DocumentSearchMatch(
                        "Pom Pom sleeps near the window.",
                        "cats.txt",
                        "/documents/cats.txt",
                        0,
                        12L,
                        "document-12",
                        0.2
                )));
        when(chatModelService.generateAnswer(anyString()))
                .thenReturn("Pom Pom sleeps near the window.");

        mockMvc.perform(post("/api/questions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "question": "Where does Pom Pom sleep?"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("Pom Pom sleeps near the window."))
                .andExpect(jsonPath("$.sources[0].fileName").value("cats.txt"))
                .andExpect(jsonPath("$.sources[0].filePath").value("/documents/cats.txt"))
                .andExpect(jsonPath("$.sources[0].chunkNumber").value(0))
                .andExpect(jsonPath("$.sources[0].text").value("Pom Pom sleeps near the window."));
    }
}
