package com.onlineinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import com.onlineinterview.interview.domain.QuestionComposition;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class AiQuestionClientHttpTest {
    @Test
    void sendsBodyOverHttp11ToRealServer() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var protocol = new AtomicReference<String>();
        var receivedBody = new AtomicReference<JsonNode>();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/internal/v1/questions:generate", exchange -> {
            protocol.set(exchange.getProtocol());
            receivedBody.set(mapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                    {
                      "requestId": "00000000-0000-0000-0000-000000000001",
                      "interviewId": "00000000-0000-0000-0000-000000000002",
                      "modelPolicy": "interview-question-model",
                      "promptVersion": "direct-question-v1",
                      "questions": [{
                        "order": 1,
                        "prompt": "Explain Java memory visibility.",
                        "maxScore": 10,
                        "type": "LONG_TEXT",
                        "options": [],
                        "correctAnswers": []
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            var client = new AiQuestionClient(RestClient.builder(), mapper,
                    "http://127.0.0.1:" + server.getAddress().getPort(), "service-token");
            client.generate(new AiQuestionClient.GenerationRequest(
                    UUID.fromString("00000000-0000-0000-0000-000000000001"),
                    UUID.fromString("00000000-0000-0000-0000-000000000002"),
                    List.of("Java"), "MEDIUM", 1, QuestionComposition.allLongText(1)));

            assertThat(protocol.get()).isEqualTo("HTTP/1.1");
            assertThat(receivedBody.get().get("questionCount").asInt()).isEqualTo(1);
            assertThat(receivedBody.get().get("skills").get(0).asText()).isEqualTo("Java");
            assertThat(receivedBody.get().get("questionComposition").get("longText").asInt())
                    .isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }
}
