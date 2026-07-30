package com.onlineinterview.mcp.application;

import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class McpResourceAuthorizationService {
    private final InterviewDefinitionRepository interviews;
    private final InterviewSessionRepository sessions;

    public McpResourceAuthorizationService(InterviewDefinitionRepository interviews,
            InterviewSessionRepository sessions) {
        this.interviews = interviews;
        this.sessions = sessions;
    }

    public boolean isOwnedBy(String resourceType, UUID resourceId, String actorSubject) {
        return switch (resourceType) {
            case "INTERVIEW" -> interviews.findById(resourceId)
                    .map(value -> value.getOwnerSubject().equals(actorSubject)).orElse(false);
            case "SESSION" -> sessions.findById(resourceId)
                    .map(value -> value.getAssignment().getInterviewDefinition()
                            .getOwnerSubject().equals(actorSubject))
                    .orElse(false);
            default -> false;
        };
    }
}
