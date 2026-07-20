package com.example.localdocumentassistant.questionanswering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

class OllamaChatModelServiceTest {

    @Test
    void sendsNonStreamingChatRequestAndReturnsMessageContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OllamaChatModelService service = new OllamaChatModelService(
                builder,
                "http://localhost:11434",
                "qwen2.5:3b",
                false,
                null
        );
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen2.5:3b",
                          "stream": false,
                          "think": false,
                          "messages": [
                            {"role": "user", "content": "Use this context"}
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen2.5:3b",
                          "message": {
                            "role": "assistant",
                            "content": "Answer from Ollama",
                            "thinking": "Hidden thinking output"
                          },
                          "done": true,
                          "done_reason": "stop",
                          "total_duration": 379420000000,
                          "load_duration": 120000000,
                          "prompt_eval_count": 900,
                          "prompt_eval_duration": 8500000000,
                          "eval_count": 2200,
                          "eval_duration": 370000000000
                        }
                        """, MediaType.APPLICATION_JSON));

        String answer = service.generateAnswer("Use this context");

        assertThat(answer).isEqualTo("Answer from Ollama");
        server.verify();
    }

    @Test
    void includesThinkTrueInRequestWhenConfigured() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OllamaChatModelService service = new OllamaChatModelService(
                builder,
                "http://localhost:11434",
                "qwen2.5:3b",
                true,
                null
        );
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen2.5:3b",
                          "stream": false,
                          "think": true,
                          "messages": [
                            {"role": "user", "content": "Use this context"}
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "message": {
                            "role": "assistant",
                            "content": "Answer from Ollama"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        String answer = service.generateAnswer("Use this context");

        assertThat(answer).isEqualTo("Answer from Ollama");
        server.verify();
    }

    @Test
    void metricsHelpersHandleMissingValues() {
        assertThat(OllamaChatModelService.lengthOf(null)).isZero();
        assertThat(OllamaChatModelService.nanosToMillis(null)).isEqualTo(-1);
        assertThat(OllamaChatModelService.tokensPerSecond(null, 1_000_000_000L)).isEqualTo(-1.0);
        assertThat(OllamaChatModelService.tokensPerSecond(10L, null)).isEqualTo(-1.0);
        assertThat(OllamaChatModelService.tokensPerSecond(10L, 0L)).isEqualTo(-1.0);
        assertThat(OllamaChatModelService.share(5, 0)).isEqualTo(0.0);
    }

    @Test
    void metricsHelpersCalculateNormalValues() {
        assertThat(OllamaChatModelService.lengthOf("answer")).isEqualTo(6);
        assertThat(OllamaChatModelService.nanosToMillis(1_500_000L)).isEqualTo(1);
        assertThat(OllamaChatModelService.tokensPerSecond(200L, 2_000_000_000L)).isEqualTo(100.0);
        assertThat(OllamaChatModelService.share(25, 100)).isEqualTo(0.25);
    }

    @Test
    void deserializesOllamaDiagnosticsFieldsAndIgnoresUnknownFields() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();

        OllamaChatModelService.OllamaChatResponse response = objectMapper.readValue("""
                {
                  "message": {
                    "role": "assistant",
                    "content": "Final answer",
                    "thinking": "Thinking output",
                    "ignored_message_field": "ignored"
                  },
                  "done_reason": "stop",
                  "total_duration": 379420000000,
                  "load_duration": 120000000,
                  "prompt_eval_count": 900,
                  "prompt_eval_duration": 8500000000,
                  "eval_count": 2200,
                  "eval_duration": 370000000000,
                  "ignored_response_field": "ignored"
                }
                """, OllamaChatModelService.OllamaChatResponse.class);

        assertThat(response.message().content()).isEqualTo("Final answer");
        assertThat(response.message().thinking()).isEqualTo("Thinking output");
        assertThat(response.doneReason()).isEqualTo("stop");
        assertThat(response.totalDuration()).isEqualTo(379420000000L);
        assertThat(response.loadDuration()).isEqualTo(120000000L);
        assertThat(response.promptEvalCount()).isEqualTo(900L);
        assertThat(response.promptEvalDuration()).isEqualTo(8500000000L);
        assertThat(response.evalCount()).isEqualTo(2200L);
        assertThat(response.evalDuration()).isEqualTo(370000000000L);
    }
}
