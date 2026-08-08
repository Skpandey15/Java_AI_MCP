package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.session.infrastructure.InterviewAnswerRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Question Bank MCP tool: reports whether a question has already been asked in this session,
 *  so an adaptive agent never repeats itself. Read-only and bound to the authorized session. */
@Component
public class QuestionReuseToolHandler implements McpToolHandler {
    private final InterviewAnswerRepository answers;
    private final ObjectMapper mapper;

    public QuestionReuseToolHandler(InterviewAnswerRepository answers, ObjectMapper mapper) {
        this.answers = answers;
        this.mapper = mapper;
    }

    @Override public String serverKey() { return "question-bank"; }
    @Override public String toolName() { return "check_question_reuse"; }

    @Override
    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        UUID sessionId = InterviewContextToolHandler.parseId(arguments, "sessionId");
        UUID questionId = InterviewContextToolHandler.parseId(arguments, "questionId");
        if (!"SESSION".equals(context.resourceType()) || !sessionId.equals(context.resourceId())) {
            throw new McpProtocolException(-32003, "MCP resource binding mismatch");
        }
        var output = mapper.createObjectNode();
        answers.findBySession_IdAndQuestion_Id(sessionId, questionId).ifPresentOrElse(
                answer -> {
                    output.put("reused", true);
                    output.put("previouslyAskedAt", answer.getUpdatedAt().toString());
                },
                () -> output.put("reused", false));
        return output;
    }
}
