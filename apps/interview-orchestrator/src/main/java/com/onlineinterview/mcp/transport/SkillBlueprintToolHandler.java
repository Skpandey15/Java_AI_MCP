package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Interview MCP tool: returns the skill blueprint an agent assesses against — the skills,
 *  the target difficulty, the passing bar, and how many questions to plan for. Read-only and
 *  bound to the authorized interview. */
@Component
public class SkillBlueprintToolHandler implements McpToolHandler {
    private final InterviewDefinitionRepository interviews;
    private final ObjectMapper mapper;

    public SkillBlueprintToolHandler(
            InterviewDefinitionRepository interviews, ObjectMapper mapper) {
        this.interviews = interviews;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "interview"; }
    @Override public String toolName() { return "get_skill_blueprint"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        UUID requested = InterviewContextToolHandler.parseId(arguments, "interviewId");
        if (!requested.equals(context.resourceId()) || !"INTERVIEW".equals(context.resourceType())) {
            throw new McpProtocolException(-32003, "MCP resource binding mismatch");
        }
        var interview = interviews.findById(requested)
                .filter(value -> value.getOwnerSubject().equals(context.actorSubject()))
                .orElseThrow(() -> new McpProtocolException(-32003, "Interview is not authorized"));
        String difficulty = interview.getDifficulty().name();
        var skills = mapper.createArrayNode();
        interview.getSkills().forEach(skill -> skills.addObject()
                .put("name", skill)
                .put("targetDifficulty", difficulty));
        var output = mapper.createObjectNode();
        output.set("skills", skills);
        output.put("targetDifficulty", difficulty);
        output.put("passingPercentage", interview.getPassingPercentage());
        output.put("questionCount", interview.getQuestionCount());
        return output;
    }
}
