package com.onlineinterview.session.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AdaptiveInterviewClientTest {
    @Test
    void postsTurnRequestWithServiceTokenAndParsesResponse() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AdaptiveInterviewClient(
                builder.baseUrl("http://ai-service:8000").build(), "svc-token");
        var questionId = UUID.randomUUID();

        server.expect(requestTo("http://ai-service:8000/internal/v1/interview:next-turn"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "svc-token"))
                .andRespond(withSuccess("""
                        {
                          "action": "ASK",
                          "rationale": "probe concurrency",
                          "question": {"skill":"Concurrency","difficulty":"HARD","source":"BANK",
                            "questionId":"%s","prompt":"Explain the JMM.","citationChunkIds":[]},
                          "lastAnswerEvaluation": {"skill":"Concurrency","score":70,
                            "confidence":80,"rationale":"solid"},
                          "finalAssessment": null,
                          "usage": {"promptTokens":10,"completionTokens":5,"totalTokens":15,
                            "estimatedCostUsd":0.0,"latencyMs":12}
                        }""".formatted(questionId), MediaType.APPLICATION_JSON));

        var request = new AdaptiveInterviewClient.NextTurnRequest(
                UUID.randomUUID(), UUID.randomUUID(), List.of("Concurrency"), "HARD", 70,
                List.of(), List.of(), List.of(), List.of(),
                new AdaptiveInterviewClient.TurnBudget(6, 50000));
        var response = client.nextTurn(request);

        assertThat(response.action()).isEqualTo("ASK");
        assertThat(response.question().questionId()).isEqualTo(questionId);
        assertThat(response.question().prompt()).isEqualTo("Explain the JMM.");
        assertThat(response.lastAnswerEvaluation().score()).isEqualTo(70);
        assertThat(response.usage().totalTokens()).isEqualTo(15);
        server.verify();
    }
}
