package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import org.springframework.stereotype.Component;

@Component
public class QuestionSearchToolHandler implements McpToolHandler {
    private final InterviewDefinitionRepository interviews;
    private final ManualQuestionRepository questions;
    private final ObjectMapper mapper;

    public QuestionSearchToolHandler(InterviewDefinitionRepository interviews,
            ManualQuestionRepository questions, ObjectMapper mapper) {
        this.interviews = interviews;
        this.questions = questions;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "question-bank"; }
    @Override public String toolName() { return "search_approved_questions"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        if (!"INTERVIEW".equals(context.resourceType())) {
            throw new McpProtocolException(-32003, "MCP resource binding mismatch");
        }
        String skill = arguments.path("skill").asText("").trim();
        int limit = arguments.path("limit").asInt(10);
        if (skill.isBlank() || limit < 1 || limit > 20) {
            throw new McpProtocolException(-32602, "Skill and a limit from 1 to 20 are required");
        }
        var interview = interviews.findById(context.resourceId())
                .filter(value -> value.getOwnerSubject().equals(context.actorSubject()))
                .filter(value -> value.getSkills().stream().anyMatch(skill::equalsIgnoreCase))
                .orElseThrow(() -> new McpProtocolException(
                        -32003, "Interview skill is not authorized"));
        var ids = questions.findByInterviewDefinitionIdOrderByOrderAsc(interview.getId()).stream()
                .limit(limit).map(question -> question.getId().toString()).toList();
        var output = mapper.createObjectNode();
        output.set("questionIds", mapper.valueToTree(ids));
        return output;
    }
}
