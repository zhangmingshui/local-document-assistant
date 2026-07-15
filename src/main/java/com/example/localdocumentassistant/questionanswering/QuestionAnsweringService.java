package com.example.localdocumentassistant.questionanswering;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.localdocumentassistant.indexing.DocumentSearchMatch;
import com.example.localdocumentassistant.indexing.DocumentSearchService;

@Service
public class QuestionAnsweringService {
    Logger LOGGER = LoggerFactory.getLogger(QuestionAnsweringService.class);

    static final String NO_RELEVANT_INFORMATION =
            "I could not find relevant information in the indexed documents.";

    private final DocumentSearchService documentSearchService;
    private final ChatModelService chatModelService;
    private final QuestionAnsweringPromptBuilder promptBuilder;
    private final int searchLimit;
    private final int maxContextChunks;
    private final double minRelevance;

    public QuestionAnsweringService(
            DocumentSearchService documentSearchService,
            ChatModelService chatModelService,
            QuestionAnsweringPromptBuilder promptBuilder,
            @Value("${app.rag.search-limit:8}") int searchLimit,
            @Value("${app.rag.max-context-chunks:2}") int maxContextChunks,
            @Value("${app.rag.min-relevance:0.5}") double minRelevance
    ) {
        this.documentSearchService = documentSearchService;
        this.chatModelService = chatModelService;
        this.promptBuilder = promptBuilder;
        this.searchLimit = searchLimit;
        this.maxContextChunks = maxContextChunks;
        this.minRelevance = minRelevance;
    }

    public QuestionAnsweringResult answer(String question) {
        String requestId = UUID.randomUUID().toString().substring(0, 8);
        long requestStartNanos = System.nanoTime();

        long searchStartNanos = System.nanoTime();
        List<DocumentSearchMatch> retrievedMatches = documentSearchService.search(question, searchLimit);
        long searchMs = elapsedMillis(searchStartNanos);

        long filterStartNanos = System.nanoTime();
        List<DocumentSearchMatch> matches = retrievedMatches.stream()
                .filter(match -> match.relevance() >= minRelevance)
                .limit(maxContextChunks)
                .toList();
        long filterMs = elapsedMillis(filterStartNanos);

        LOGGER.info(
                "QA_RETRIEVAL requestId={} query=\"{}\" questionChars={} returned={} kept={} minRelevance={} maxContextChunks={}",
                requestId,
                question,
                question.length(),
                retrievedMatches.size(),
                matches.size(),
                minRelevance,
                maxContextChunks
        );
        matches.forEach(match -> LOGGER.info(
                "QA_RETRIEVAL_CHUNK requestId={} fileName={} chunkIndex={} distance={} relevance={} textChars={}",
                requestId,
                match.fileName(),
                match.chunkIndex(),
                match.distance(),
                match.relevance(),
                match.text() == null ? 0 : match.text().length()
        ));

        if (matches.isEmpty()) {
            logMetrics(
                    requestId,
                    requestStartNanos,
                    searchMs,
                    filterMs,
                    0,
                    0,
                    question.length(),
                    retrievedMatches.size(),
                    0,
                    0,
                    NO_RELEVANT_INFORMATION.length()
            );
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

        long promptStartNanos = System.nanoTime();
        String prompt = promptBuilder.build(question, matches);
        long promptMs = elapsedMillis(promptStartNanos);

        long llmStartNanos = System.nanoTime();
        String answer = chatModelService.generateAnswer(prompt);
        long llmMs = elapsedMillis(llmStartNanos);

        logMetrics(
                requestId,
                requestStartNanos,
                searchMs,
                filterMs,
                promptMs,
                llmMs,
                question.length(),
                retrievedMatches.size(),
                matches.size(),
                prompt.length(),
                answer == null ? 0 : answer.length()
        );

        return new QuestionAnsweringResult(answer, sources);
    }

    private void logMetrics(
            String requestId,
            long requestStartNanos,
            long searchMs,
            long filterMs,
            long promptMs,
            long llmMs,
            int questionChars,
            int retrievedChunks,
            int keptChunks,
            int promptChars,
            int answerChars
    ) {
        LOGGER.info(
                "QA_METRICS requestId={} totalMs={} searchMs={} filterMs={} promptMs={} llmMs={} "
                        + "questionChars={} retrievedChunks={} keptChunks={} promptChars={} answerChars={} "
                        + "searchLimit={} maxContextChunks={} minRelevance={}",
                requestId,
                elapsedMillis(requestStartNanos),
                searchMs,
                filterMs,
                promptMs,
                llmMs,
                questionChars,
                retrievedChunks,
                keptChunks,
                promptChars,
                answerChars,
                searchLimit,
                maxContextChunks,
                minRelevance
        );
    }

    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
