package com.onlineinterview.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.common.api.CorrelationIdFilter;
import com.onlineinterview.common.resilience.DownstreamCallExecutor;
import com.onlineinterview.interview.domain.QuestionComposition;
import com.onlineinterview.session.domain.QuestionType;
import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AiQuestionClient {
    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final String serviceToken;
    private final DownstreamCallExecutor resilience;

    @Autowired
    public AiQuestionClient(RestClient.Builder builder, ObjectMapper objectMapper,
            ObjectProvider<DownstreamCallExecutor> resilience,
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
        this.resilience = resilience.getIfAvailable();
    }

    AiQuestionClient(RestClient client, ObjectMapper objectMapper, String serviceToken) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.serviceToken = serviceToken;
        this.resilience = null;
    }

    AiQuestionClient(RestClient.Builder builder, ObjectMapper objectMapper,
            String baseUrl, String serviceToken) {
        this(builder.baseUrl(baseUrl).build(), objectMapper, serviceToken);
    }

    public GenerationResponse generate(GenerationRequest request) {
        var call = (java.util.function.Supplier<GenerationResponse>) () -> client.post()
                .uri("/internal/v1/questions:generate")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .headers(headers -> {
                    var requestId = MDC.get("requestId");
                    if (requestId != null) headers.set(CorrelationIdFilter.HEADER, requestId);
                })
                .body(jsonBody(request))
                .retrieve()
                .body(GenerationResponse.class);
        return resilience == null ? call.get() : resilience.execute("ai-question-service", call);
    }

    public ComposeResponse compose(ComposeRequest request) {
        var call = (java.util.function.Supplier<ComposeResponse>) () -> client.post()
                .uri("/internal/v1/questions:compose")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(request)
                .retrieve()
                .body(ComposeResponse.class);
        return resilience == null ? call.get() : resilience.execute("ai-composition-service", call);
    }

    public record ComposeRequest(List<String> skills, String difficulty, int questionCount,
            QuestionComposition composition, List<String> existingPrompts, List<String> grounding,
            int maxRounds) {}
    public record ComposedQuestion(String prompt, String topic, QuestionType type,
            List<String> options, List<String> correctAnswers) {}
    public record ComposeResponse(List<ComposedQuestion> questions, int rounds, List<String> trace) {}

    private byte[] jsonBody(GenerationRequest request) {
        try {
            return objectMapper.writeValueAsBytes(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize AI generation request", exception);
        }
    }

    public record GenerationRequest(UUID requestId, UUID interviewId, List<String> skills,
            String difficulty, int questionCount, QuestionComposition questionComposition,
            List<GroundingChunk> groundingContext) {}
    public record GroundingChunk(UUID citationId, String sourceName, String content) {}
    public record GeneratedQuestion(
            int order, String prompt, int maxScore, QuestionType type,
            List<String> options, List<String> correctAnswers, List<UUID> citationIds) {}
    public record GenerationResponse(UUID requestId, UUID interviewId, String modelPolicy,
            String promptVersion, List<GeneratedQuestion> questions) {}
}
