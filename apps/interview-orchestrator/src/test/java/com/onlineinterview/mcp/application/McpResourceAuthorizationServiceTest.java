package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpResourceAuthorizationServiceTest {
    private final InterviewDefinitionRepository interviews =
            mock(InterviewDefinitionRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final McpResourceAuthorizationService service =
            new McpResourceAuthorizationService(interviews, sessions);

    @Test
    void authorizesOnlyResourcesOwnedByActor() {
        var interviewId = UUID.randomUUID();
        var definition = mock(InterviewDefinition.class);
        when(definition.getOwnerSubject()).thenReturn("owner");
        when(interviews.findById(interviewId)).thenReturn(Optional.of(definition));

        assertThat(service.isOwnedBy("INTERVIEW", interviewId, "owner")).isTrue();
        assertThat(service.isOwnedBy("INTERVIEW", interviewId, "other")).isFalse();
        assertThat(service.isOwnedBy("INTERVIEW", UUID.randomUUID(), "owner")).isFalse();

        var sessionId = UUID.randomUUID();
        var session = mock(InterviewSession.class);
        var assignment = mock(InterviewAssignment.class);
        when(session.getAssignment()).thenReturn(assignment);
        when(assignment.getInterviewDefinition()).thenReturn(definition);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(session));

        assertThat(service.isOwnedBy("SESSION", sessionId, "owner")).isTrue();
        assertThat(service.isOwnedBy("SESSION", sessionId, "other")).isFalse();
        assertThat(service.isOwnedBy("SESSION", UUID.randomUUID(), "owner")).isFalse();
        assertThat(service.isOwnedBy("UNKNOWN", interviewId, "owner")).isFalse();
    }
}
