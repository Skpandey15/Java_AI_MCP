package com.onlineinterview.ai;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiQuestionClient {
    private final RestClient client;
    private final String serviceToken;

    public AiQuestionClient(RestClient.Builder builder,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        this.client = builder.baseUrl(baseUrl).build();
        this.serviceToken = serviceToken;
    }

    public GenerationResponse generate(GenerationRequest request) {
        return client.post()
                .uri("/internal/v1/questions:generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(request)
                .retrieve()
                .body(GenerationResponse.class);
    }

    public record GenerationRequest(UUID requestId, UUID interviewId, List<String> skills,
            String difficulty, int questionCount) {}
    public record GeneratedQuestion(int order, String prompt, int maxScore) {}
    public record GenerationResponse(UUID requestId, UUID interviewId, String modelPolicy,
            String promptVersion, List<GeneratedQuestion> questions) {}
}
