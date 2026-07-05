package com.example.localdocumentassistant.questionanswering;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OllamaChatModelServiceTest {

    @Test
    void sendsNonStreamingChatRequestAndReturnsMessageContent() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        OllamaChatModelService service = new OllamaChatModelService(
                builder,
                "http://localhost:11434",
                "qwen3:8b"
        );
        server.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(content().json("""
                        {
                          "model": "qwen3:8b",
                          "stream": false,
                          "messages": [
                            {"role": "user", "content": "Use this context"}
                          ]
                        }
                        """))
                .andRespond(withSuccess("""
                        {
                          "model": "qwen3:8b",
                          "message": {
                            "role": "assistant",
                            "content": "Answer from Ollama"
                          },
                          "done": true
                        }
                        """, MediaType.APPLICATION_JSON));

        String answer = service.generateAnswer("Use this context");

        assertThat(answer).isEqualTo("Answer from Ollama");
        server.verify();
    }
}
