package com.onlineinterview.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.json.JsonMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AiQuestionClientTest {
    @Test
    void sendsCamelCaseJsonBodyAndServiceToken() {
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new AiQuestionClient(builder.baseUrl("http://localhost:8000").build(),
                JsonMapper.builder().findAndAddModules().build(), "service-token");
        UUID requestId = UUID.randomUUID();
        UUID interviewId = UUID.randomUUID();

        server.expect(request -> assertThat(request.getURI().getPath())
                        .isEqualTo("/internal/v1/questions:generate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Token", "service-token"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "requestId": "%s",
                          "interviewId": "%s",
                          "skills": ["Java"],
                          "difficulty": "MEDIUM",
                          "questionCount": 1
                        }
                        """.formatted(requestId, interviewId)))
                .andRespond(withSuccess("""
                        {
                          "requestId": "%s",
                          "interviewId": "%s",
                          "modelPolicy": "interview-question-model",
                          "promptVersion": "direct-question-v1",
                          "questions": [{
                            "order": 1,
                            "prompt": "Explain Java memory visibility.",
                            "maxScore": 10
                          }]
                        }
                        """.formatted(requestId, interviewId), MediaType.APPLICATION_JSON));

        var response = client.generate(new AiQuestionClient.GenerationRequest(
                requestId, interviewId, List.of("Java"), "MEDIUM", 1));

        assertThat(response.questions()).hasSize(1);
        server.verify();
    }
}
