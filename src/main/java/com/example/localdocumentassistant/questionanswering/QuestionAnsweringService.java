package com.example.localdocumentassistant.questionanswering;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

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
            @Value("${app.rag.max-context-chunks:3}") int maxContextChunks,
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
        List<DocumentSearchMatch> retrievedMatches = documentSearchService.search(question, searchLimit);
        List<DocumentSearchMatch> matches = retrievedMatches.stream()
                .filter(match -> match.relevance() >= minRelevance)
                .limit(maxContextChunks)
                .toList();

        LOGGER.info(
                "Retrieved chunks for question query=\"{}\" returned={} kept={} minRelevance={} maxContextChunks={}",
                question,
                retrievedMatches.size(),
                matches.size(),
                minRelevance,
                maxContextChunks
        );
        matches.forEach(match -> LOGGER.info(
                "Kept chunk fileName={} chunkIndex={} distance={} relevance={}",
                match.fileName(),
                match.chunkIndex(),
                match.distance(),
                match.relevance()
        ));

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
        LocalDateTime start = LocalDateTime.now();
        LOGGER.info("START Ask llm " + start);
        String prompt = promptBuilder.build(question, matches);
        String answer = chatModelService.generateAnswer(prompt);
        LocalDateTime end = LocalDateTime.now();
        LOGGER.info("END Ask llm " + end);

        Duration elapsed = Duration.between(start, end);

        long hours = elapsed.toHours();
        long minutes = elapsed.toMinutesPart();
        long seconds = elapsed.toSecondsPart();

        String display = "%d hours, %d minutes, %d seconds".formatted(hours, minutes, seconds);
        LOGGER.info("Time elapsed: " + display);

        return new QuestionAnsweringResult(answer, sources);
    }
}
