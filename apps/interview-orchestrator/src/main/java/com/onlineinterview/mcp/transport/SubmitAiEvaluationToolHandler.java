package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.mcp.domain.McpAiEvaluation;
import com.onlineinterview.mcp.infrastructure.McpAiEvaluationRepository;
import com.onlineinterview.session.domain.SessionState;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Clock;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class SubmitAiEvaluationToolHandler implements McpToolHandler {
    private final InterviewSessionRepository sessions;
    private final McpAiEvaluationRepository evaluations;
    private final ManualQuestionRepository questions;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public SubmitAiEvaluationToolHandler(InterviewSessionRepository sessions,
            McpAiEvaluationRepository evaluations, ManualQuestionRepository questions,
            ObjectMapper mapper) {
        this(sessions, evaluations, questions, mapper, Clock.systemUTC());
    }

    SubmitAiEvaluationToolHandler(InterviewSessionRepository sessions,
            McpAiEvaluationRepository evaluations, ManualQuestionRepository questions,
            ObjectMapper mapper, Clock clock) {
        this.sessions = sessions;
        this.evaluations = evaluations;
        this.questions = questions;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override public String serverKey() { return "result"; }
    @Override public String toolName() { return "submit_ai_evaluation"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        var sessionId = InterviewContextToolHandler.parseId(arguments, "sessionId");
        int score = arguments.path("score").asInt(-1);
        if (!"SESSION".equals(context.resourceType())
                || !sessionId.equals(context.resourceId()) || score < 0) {
            throw new McpProtocolException(-32602, "Session binding and non-negative score required");
        }
        var session = sessions.findById(sessionId)
                .filter(value -> value.getState() == SessionState.SUBMITTED
                        && value.getReviewStatus()
                        == com.onlineinterview.session.domain.ReviewStatus.PENDING_REVIEW)
                .orElseThrow(() -> new McpProtocolException(
                        -32003, "Submitted session is not available"));
        int maximum = questions.findByInterviewDefinitionIdOrderByOrderAsc(
                session.getAssignment().getInterviewDefinition().getId())
                .stream().mapToInt(value -> value.getMaxScore()).sum();
        if (maximum <= 0 || score > maximum) {
            throw new McpProtocolException(-32602, "Score exceeds the interview maximum");
        }
        evaluations.save(McpAiEvaluation.pending(
                sessionId, context.contextId(), score, clock.instant()));
        return mapper.createObjectNode().put("accepted", true);
    }
}
