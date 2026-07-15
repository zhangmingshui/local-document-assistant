package com.example.localdocumentassistant.questionanswering;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@Service
@Profile("custom-ollama")
public class OllamaChatModelService implements ChatModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaChatModelService.class);

    private final RestClient restClient;
    private final String chatModel;
    private final Boolean think;

    public OllamaChatModelService(
            RestClient.Builder restClientBuilder,
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.chat-model:qwen3:8b}") String chatModel,
            @Value("${local-document-assistant.ollama.think:false}") Boolean think
    ) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.chatModel = chatModel;
        this.think = think;
    }

    @Override
    public String generateAnswer(String prompt) {
        long startNanos = System.nanoTime();
        try {
            OllamaChatResponse response = restClient.post()
                    .uri("/api/chat")
                    .body(new OllamaChatRequest(
                            chatModel,
                            false,
                            think,
                            List.of(new OllamaMessage("user", prompt, null))
                    ))
                    .retrieve()
                    .body(OllamaChatResponse.class);

            if (response == null || response.message() == null
                    || response.message().content() == null
                    || response.message().content().isBlank()) {
                logChatMetrics(prompt, response, startNanos);
                throw new ChatModelUnavailableException("Ollama returned no answer.");
            }
            String answer = response.message().content();
            logChatMetrics(prompt, response, startNanos);
            return answer;
        } catch (RestClientException error) {
            logChatMetrics(prompt, null, startNanos);
            throw new ChatModelUnavailableException(
                    "Ollama chat is unavailable. Check that Ollama is running and the configured chat model is installed.",
                    error
            );
        }
    }

    private void logChatMetrics(String prompt, OllamaChatResponse response, long startNanos) {
        OllamaMessage message = response == null ? null : response.message();
        int answerChars = message == null ? 0 : lengthOf(message.content());
        int thinkingChars = message == null ? 0 : lengthOf(message.thinking());
        int outputChars = answerChars + thinkingChars;

        LOGGER.info(
                "CHAT_MODEL_METRICS provider=custom-ollama model={} think={} promptChars={} elapsedMs={} "
                        + "ollamaTotalMs={} loadMs={} promptEvalTokens={} promptEvalMs={} promptTokensPerSecond={} "
                        + "evalTokens={} evalMs={} evalTokensPerSecond={} answerChars={} thinkingChars={} "
                        + "outputChars={} thinkingShare={} doneReason={}",
                chatModel,
                think,
                lengthOf(prompt),
                (System.nanoTime() - startNanos) / 1_000_000,
                nanosToMillis(response == null ? null : response.totalDuration()),
                nanosToMillis(response == null ? null : response.loadDuration()),
                response == null ? null : response.promptEvalCount(),
                nanosToMillis(response == null ? null : response.promptEvalDuration()),
                tokensPerSecond(
                        response == null ? null : response.promptEvalCount(),
                        response == null ? null : response.promptEvalDuration()
                ),
                response == null ? null : response.evalCount(),
                nanosToMillis(response == null ? null : response.evalDuration()),
                tokensPerSecond(
                        response == null ? null : response.evalCount(),
                        response == null ? null : response.evalDuration()
                ),
                answerChars,
                thinkingChars,
                outputChars,
                share(thinkingChars, outputChars),
                response == null ? null : response.doneReason()
        );
    }

    static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    static long nanosToMillis(Long nanos) {
        return nanos == null ? -1 : nanos / 1_000_000;
    }

    static double tokensPerSecond(Long tokens, Long durationNanos) {
        if (tokens == null || durationNanos == null || durationNanos == 0) {
            return -1.0;
        }
        return tokens / (durationNanos / 1_000_000_000.0);
    }

    static double share(int part, int total) {
        if (total == 0) {
            return 0.0;
        }
        return (double) part / total;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OllamaChatRequest(String model, boolean stream, Boolean think, List<OllamaMessage> messages) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaMessage(String role, String content, String thinking) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OllamaChatResponse(
            OllamaMessage message,
            @JsonProperty("done_reason") String doneReason,
            @JsonProperty("total_duration") Long totalDuration,
            @JsonProperty("load_duration") Long loadDuration,
            @JsonProperty("prompt_eval_count") Long promptEvalCount,
            @JsonProperty("prompt_eval_duration") Long promptEvalDuration,
            @JsonProperty("eval_count") Long evalCount,
            @JsonProperty("eval_duration") Long evalDuration
    ) {
    }
}
