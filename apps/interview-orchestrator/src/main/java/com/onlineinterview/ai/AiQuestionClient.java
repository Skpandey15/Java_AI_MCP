package com.onlineinterview.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiQuestionClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String serviceToken;

    @Autowired
    public AiQuestionClient(RestClient.Builder builder, ObjectMapper objectMapper,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        this.client = builder
                .baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient))
                .build();
        this.objectMapper = objectMapper;
        this.serviceToken = serviceToken;
    }

    AiQuestionClient(RestClient client, ObjectMapper objectMapper, String serviceToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.serviceToken = serviceToken;
    }

    public GenerationResponse generate(GenerationRequest request) {
        return client.post()
                .uri("/internal/v1/questions:generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(jsonBody(request))
                .retrieve()
                .body(GenerationResponse.class);
    }

    private byte[] jsonBody(GenerationRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize AI generation request", exception);
        }
    }

    public record GenerationRequest(UUID requestId, UUID interviewId, List<String> skills,
            String difficulty, int questionCount) {}
    public record GeneratedQuestion(int order, String prompt, int maxScore) {}
    public record GenerationResponse(UUID requestId, UUID interviewId, String modelPolicy,
            String promptVersion, List<GeneratedQuestion> questions) {}
}
