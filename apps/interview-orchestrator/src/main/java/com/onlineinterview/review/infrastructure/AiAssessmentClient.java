package com.onlineinterview.review.infrastructure;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the Python AI service's Assess &amp; Coach agents through a scoped service token. */
@Component
public class AiAssessmentClient {
    private final RestClient client;
    private final String serviceToken;

    public AiAssessmentClient(RestClient.Builder builder,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.client = builder.baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient)).build();
        this.serviceToken = serviceToken;
    }

    public List<Suggestion> evaluate(UUID sessionId, List<EvalItem> items) {
        var response = client.post().uri("/internal/v1/answers:evaluate")
                .contentType(MediaType.APPLICATION_JSON).header("X-Service-Token", serviceToken)
                .body(new EvalRequest(sessionId, items)).retrieve().body(EvalResponse.class);
        return response == null || response.evaluations() == null ? List.of() : response.evaluations();
    }

    public DraftResponse draftFeedback(UUID sessionId, String outcome, boolean passed,
            List<FbItem> items) {
        return client.post().uri("/internal/v1/feedback:draft")
                .contentType(MediaType.APPLICATION_JSON).header("X-Service-Token", serviceToken)
                .body(new DraftRequest(sessionId, outcome, passed, items))
                .retrieve().body(DraftResponse.class);
    }

    public List<ModelAnswer> modelAnswers(List<ModelItem> items) {
        var response = client.post().uri("/internal/v1/answers:model")
                .contentType(MediaType.APPLICATION_JSON).header("X-Service-Token", serviceToken)
                .body(new ModelRequest(items)).retrieve().body(ModelResponse.class);
        return response == null || response.answers() == null ? List.of() : response.answers();
    }

    /** Candidate-answer-aware detailed explanation for a single wrong/partial answer. */
    public ExplainResponse explainAnswer(ExplainRequest request) {
        return client.post().uri("/internal/v1/answers:explain")
                .contentType(MediaType.APPLICATION_JSON).header("X-Service-Token", serviceToken)
                .body(request).retrieve().body(ExplainResponse.class);
    }

    public record EvalItem(UUID answerId, String questionPrompt, int maxScore,
            String answerText, String topic) {}
    public record EvalRequest(UUID sessionId, List<EvalItem> items) {}
    public record Suggestion(UUID answerId, int suggestedScore, double confidence, String justification) {}
    public record EvalResponse(List<Suggestion> evaluations) {}

    public record FbItem(String questionPrompt, String topic, int maxScore, int awardedScore,
            String answerText, String evaluatorFeedback) {}
    public record DraftRequest(UUID sessionId, String outcome, boolean passed, List<FbItem> items) {}
    public record Leakage(boolean safe, List<String> flags) {}
    public record DraftResponse(String feedback, Leakage leakage) {}

    public record ModelItem(String questionPrompt, String type,
            List<String> options, List<String> correctAnswers) {}
    public record ModelRequest(List<ModelItem> items) {}
    public record ModelAnswer(String content) {}
    public record ModelResponse(List<ModelAnswer> answers) {}

    public record ExplainRequest(String questionPrompt, String type, List<String> options,
            List<String> correctAnswers, String candidateAnswer, int maxScore, int awardedScore) {}
    public record ExplainResponse(String content) {}
}
