package com.example.localdocumentassistant.questionanswering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
import com.example.localdocumentassistant.indexing.DocumentSearchService;

@ExtendWith(MockitoExtension.class)
class QuestionAnsweringServiceTest {

    @Mock
    private DocumentSearchService documentSearchService;

    @Mock
    private ChatModelService chatModelService;

    private QuestionAnsweringService questionAnsweringService;

    @BeforeEach
    void setUp() {
        questionAnsweringService = new QuestionAnsweringService(
                documentSearchService,
                chatModelService,
                8,
                3,
                0.5
        );
    }

    @Test
    void retrievesChunksBuildsPromptAndReturnsAnswerWithRetrievedSources() {
        DocumentSearchMatch firstMatch = match(
                "Pom Pom sleeps near the window.",
                "cats.txt",
                "/documents/cats.txt",
                0,
                12L,
                "document-12",
                0.2
        );
        DocumentSearchMatch secondMatch = match(
                "Miso prefers the sofa.",
                "pets.txt",
                "/documents/pets.txt",
                2,
                15L,
                "document-15",
                0.4
        );
        when(documentSearchService.search("Where does Pom Pom sleep?", 8))
                .thenReturn(List.of(firstMatch, secondMatch));
        when(chatModelService.generateAnswer(anyString())).thenReturn("Pom Pom sleeps near the window.");

        QuestionAnsweringResult result = questionAnsweringService.answer("Where does Pom Pom sleep?");

        assertThat(result.answer()).isEqualTo("Pom Pom sleeps near the window.");
        assertThat(result.sources()).containsExactly(
                new QuestionSource("cats.txt", "/documents/cats.txt", 0, firstMatch.text()),
                new QuestionSource("pets.txt", "/documents/pets.txt", 2, secondMatch.text())
        );

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModelService).generateAnswer(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("using only the supplied context")
                .contains("[Source 1: cats.txt, chunk 0]")
                .contains(firstMatch.text())
                .contains("[Source 2: pets.txt, chunk 2]")
                .contains("Question:\nWhere does Pom Pom sleep?");
    }

    @Test
    void returnsNotFoundAnswerWithoutCallingChatWhenNoChunksAreRetrieved() {
        when(documentSearchService.search("unknown topic", 8)).thenReturn(List.of());

        QuestionAnsweringResult result = questionAnsweringService.answer("unknown topic");

        assertThat(result.answer()).isEqualTo(QuestionAnsweringService.NO_RELEVANT_INFORMATION);
        assertThat(result.sources()).isEmpty();
        verify(chatModelService, never()).generateAnswer(anyString());
    }

    @Test
    void returnsNotFoundAnswerWithoutCallingChatWhenAllChunksAreBelowMinRelevance() {
        when(documentSearchService.search("weak topic", 8)).thenReturn(List.of(
                match("Weak match", "weak.txt", "/documents/weak.txt", 0, 20L, "document-20", 1.1),
                match("Weaker match", "weaker.txt", "/documents/weaker.txt", 1, 21L, "document-21", 2.0)
        ));

        QuestionAnsweringResult result = questionAnsweringService.answer("weak topic");

        assertThat(result.answer()).isEqualTo(QuestionAnsweringService.NO_RELEVANT_INFORMATION);
        assertThat(result.sources()).isEmpty();
        verify(chatModelService, never()).generateAnswer(anyString());
    }

    @Test
    void callsChatModelWhenAtLeastOneChunkMeetsMinRelevance() {
        DocumentSearchMatch keptMatch = match(
                "Useful match",
                "useful.txt",
                "/documents/useful.txt",
                0,
                22L,
                "document-22",
                1.0
        );
        when(documentSearchService.search("useful topic", 8)).thenReturn(List.of(
                match("Too far away", "far.txt", "/documents/far.txt", 0, 23L, "document-23", 1.2),
                keptMatch
        ));
        when(chatModelService.generateAnswer(anyString())).thenReturn("Useful answer");

        QuestionAnsweringResult result = questionAnsweringService.answer("useful topic");

        assertThat(result.answer()).isEqualTo("Useful answer");
        assertThat(result.sources()).containsExactly(
                new QuestionSource("useful.txt", "/documents/useful.txt", 0, keptMatch.text())
        );
        verify(chatModelService).generateAnswer(anyString());
    }

    @Test
    void limitsPromptToMaxContextChunksAfterRelevanceFiltering() {
        QuestionAnsweringService service = new QuestionAnsweringService(
                documentSearchService,
                chatModelService,
                8,
                2,
                0.5
        );
        when(documentSearchService.search("many matches", 8)).thenReturn(List.of(
                match("First kept", "first.txt", "/documents/first.txt", 0, 31L, "document-31", 0.1),
                match("Second kept", "second.txt", "/documents/second.txt", 1, 32L, "document-32", 0.2),
                match("Third filtered by context limit", "third.txt", "/documents/third.txt", 2, 33L, "document-33", 0.3)
        ));
        when(chatModelService.generateAnswer(anyString())).thenReturn("Limited answer");

        QuestionAnsweringResult result = service.answer("many matches");

        assertThat(result.sources()).hasSize(2);
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(chatModelService).generateAnswer(promptCaptor.capture());
        assertThat(promptCaptor.getValue())
                .contains("First kept")
                .contains("Second kept")
                .doesNotContain("Third filtered by context limit");
    }

    private DocumentSearchMatch match(
            String text,
            String fileName,
            String filePath,
            int chunkIndex,
            Long documentId,
            String documentUuid,
            double distance
    ) {
        return new DocumentSearchMatch(text, fileName, filePath, chunkIndex, documentId, documentUuid, distance);
    }
}
