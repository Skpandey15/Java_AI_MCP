package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class InterviewContextToolHandler implements McpToolHandler {
    private final InterviewDefinitionRepository interviews;
    private final ObjectMapper mapper;

    public InterviewContextToolHandler(
            InterviewDefinitionRepository interviews, ObjectMapper mapper) {
        this.interviews = interviews;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "interview"; }
    @Override public String toolName() { return "get_interview_context"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        UUID requested = parseId(arguments, "interviewId");
        if (!requested.equals(context.resourceId()) || !"INTERVIEW".equals(context.resourceType())) {
            throw new McpProtocolException(-32003, "MCP resource binding mismatch");
        }
        var interview = interviews.findById(requested)
                .filter(value -> value.getOwnerSubject().equals(context.actorSubject()))
                .orElseThrow(() -> new McpProtocolException(-32003, "Interview is not authorized"));
        var output = mapper.createObjectNode().put("title", interview.getTitle());
        output.set("skills", mapper.valueToTree(interview.getSkills()));
        return output;
    }

    static UUID parseId(JsonNode arguments, String name) {
        try {
            return UUID.fromString(arguments.path(name).asText());
        } catch (IllegalArgumentException exception) {
            throw new McpProtocolException(-32602, "Invalid " + name);
        }
    }
}
