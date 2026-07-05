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
        questionAnsweringService = new QuestionAnsweringService(documentSearchService, chatModelService);
    }

    @Test
    void retrievesChunksBuildsPromptAndReturnsAnswerWithRetrievedSources() {
        DocumentSearchMatch firstMatch = match(
                "Pom Pom sleeps near the window.",
                "cats.txt",
                "/documents/cats.txt",
                0,
                12L,
                "document-12"
        );
        DocumentSearchMatch secondMatch = match(
                "Miso prefers the sofa.",
                "pets.txt",
                "/documents/pets.txt",
                2,
                15L,
                "document-15"
        );
        when(documentSearchService.search("Where does Pom Pom sleep?", 3))
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
        when(documentSearchService.search("unknown topic", 3)).thenReturn(List.of());

        QuestionAnsweringResult result = questionAnsweringService.answer("unknown topic");

        assertThat(result.answer()).isEqualTo(QuestionAnsweringService.NO_RELEVANT_INFORMATION);
        assertThat(result.sources()).isEmpty();
        verify(chatModelService, never()).generateAnswer(anyString());
    }

    private DocumentSearchMatch match(
            String text,
            String fileName,
            String filePath,
            int chunkIndex,
            Long documentId,
            String documentUuid
    ) {
        return new DocumentSearchMatch(text, fileName, filePath, chunkIndex, documentId, documentUuid);
    }
}
