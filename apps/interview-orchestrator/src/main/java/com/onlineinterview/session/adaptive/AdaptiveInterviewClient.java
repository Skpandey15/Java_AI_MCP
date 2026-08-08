package com.onlineinterview.session.adaptive;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Calls the ai-service Adaptive Interviewer agent (POST /internal/v1/interview:next-turn)
 *  through a scoped service token. The orchestrator brokers all tool results into the request. */
@Component
public class AdaptiveInterviewClient {
    private final RestClient client;
    private final String serviceToken;

    @Autowired
    public AdaptiveInterviewClient(RestClient.Builder builder,
            @Value("${app.ai-service.base-url}") String baseUrl,
            @Value("${app.ai-service.service-token}") String serviceToken) {
        var httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        this.client = builder.baseUrl(baseUrl)
                .requestFactory(new JdkClientHttpRequestFactory(httpClient)).build();
        this.serviceToken = serviceToken;
    }

    AdaptiveInterviewClient(RestClient client, String serviceToken) {
        this.client = client;
        this.serviceToken = serviceToken;
    }

    public NextTurnResponse nextTurn(NextTurnRequest request) {
        return client.post().uri("/internal/v1/interview:next-turn")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Service-Token", serviceToken)
                .body(request)
                .retrieve()
                .body(NextTurnResponse.class);
    }

    // ---- wire contract (camelCase field names match the ai-service pydantic aliases) ----
    public record NextTurnRequest(UUID sessionId, UUID interviewId, List<String> skills,
            String targetDifficulty, int passingPercentage, List<TranscriptEntry> transcript,
            List<SkillMastery> skillMastery, List<CandidateQuestion> candidateQuestions,
            List<KnowledgeSnippet> knowledgeSnippets, TurnBudget budget) {}

    public record TranscriptEntry(String skill, String question, String answer) {}

    public record SkillMastery(String skill, int confidence, int evidence) {}

    public record CandidateQuestion(UUID questionId, String skill, String difficulty,
            String prompt) {}

    public record KnowledgeSnippet(UUID chunkId, String fileName, String content) {}

    public record TurnBudget(int turnsRemaining, int tokenBudget) {}

    public record NextTurnResponse(String action, String rationale, AskedQuestion question,
            AnswerEvaluation lastAnswerEvaluation, FinalAssessment finalAssessment, Usage usage) {}

    public record AskedQuestion(String skill, String difficulty, String source, UUID questionId,
            String prompt, List<UUID> citationChunkIds) {}

    public record AnswerEvaluation(String skill, int score, int confidence, String rationale) {}

    public record FinalAssessment(String summary, List<SkillMastery> perSkill) {}

    public record Usage(int promptTokens, int completionTokens, int totalTokens,
            double estimatedCostUsd, int latencyMs) {}
}
