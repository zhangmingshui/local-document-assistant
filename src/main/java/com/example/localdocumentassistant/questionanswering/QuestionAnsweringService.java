package com.example.localdocumentassistant.questionanswering;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
import com.example.localdocumentassistant.indexing.DocumentSearchService;

@Service
public class QuestionAnsweringService {

    static final int RETRIEVAL_LIMIT = 3;
    static final String NO_RELEVANT_INFORMATION =
            "I could not find relevant information in the indexed documents.";

    private final DocumentSearchService documentSearchService;
    private final ChatModelService chatModelService;

    public QuestionAnsweringService(
            DocumentSearchService documentSearchService,
            ChatModelService chatModelService
    ) {
        this.documentSearchService = documentSearchService;
        this.chatModelService = chatModelService;
    }

    public QuestionAnsweringResult answer(String question) {
        List<DocumentSearchMatch> matches = documentSearchService.search(question, RETRIEVAL_LIMIT);
        if (matches.isEmpty()) {
            return new QuestionAnsweringResult(NO_RELEVANT_INFORMATION, List.of());
        }

        List<QuestionSource> sources = matches.stream()
                .map(match -> new QuestionSource(
                        match.fileName(),
                        match.filePath(),
                        match.chunkIndex(),
                        match.text()
                ))
                .toList();

        String answer = chatModelService.generateAnswer(buildPrompt(question, matches));
        return new QuestionAnsweringResult(answer, sources);
    }

    private String buildPrompt(String question, List<DocumentSearchMatch> matches) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Answer the question using only the supplied context.\n")
                .append("If the answer is not in the context, say: \"")
                .append(NO_RELEVANT_INFORMATION)
                .append("\"\n\nContext:\n");

        for (int index = 0; index < matches.size(); index++) {
            DocumentSearchMatch match = matches.get(index);
            prompt.append("[Source ")
                    .append(index + 1)
                    .append(": ")
                    .append(match.fileName())
                    .append(", chunk ")
                    .append(match.chunkIndex())
                    .append("]\n")
                    .append(match.text())
                    .append("\n\n");
        }

        return prompt.append("Question:\n").append(question).toString();
    }
}
