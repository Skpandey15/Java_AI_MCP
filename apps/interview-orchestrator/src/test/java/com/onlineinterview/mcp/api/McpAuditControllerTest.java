package com.onlineinterview.mcp.api;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.onlineinterview.mcp.application.*;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolAuditEventRepository;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class McpAuditControllerTest {
    private final McpToolAuditEventRepository audit = mock(McpToolAuditEventRepository.class);
    private final McpResourceAuthorizationService resources =
            mock(McpResourceAuthorizationService.class);
    private final McpAuditController controller = new McpAuditController(audit, resources);
    private final Jwt jwt = mock(Jwt.class);

    @Test
    void returnsOwnedResourceAuditAndHidesUnownedResource() {
        var now = Instant.parse("2026-07-30T10:00:00Z");
        var resourceId = UUID.randomUUID();
        var context = new McpAuthorizationContext(UUID.randomUUID(),
                McpWorkflow.QUESTION_GENERATION, "interview", "get_interview_context",
                "owner", McpActorRole.INTERVIEWER, "INTERVIEW", resourceId,
                5, false, now, now.plusSeconds(60));
        var event = McpToolAuditEvent.create(
                UUID.randomUUID(), context, McpAuditEventType.SUCCEEDED, null, now);
        when(jwt.getSubject()).thenReturn("owner");
        when(resources.isOwnedBy("INTERVIEW", resourceId, "owner")).thenReturn(true);
        when(audit.findByResourceTypeAndResourceIdOrderByOccurredAtDesc(
                "INTERVIEW", resourceId)).thenReturn(List.of(event));

        assertThat(controller.list(jwt, "INTERVIEW", resourceId)).singleElement()
                .satisfies(value -> {
                    assertThat(value.id()).isEqualTo(event.getId());
                    assertThat(value.executionId()).isEqualTo(event.getExecutionId());
                    assertThat(value.contextId()).isEqualTo(context.contextId());
                    assertThat(value.workflow()).isEqualTo("QUESTION_GENERATION");
                    assertThat(value.serverKey()).isEqualTo("interview");
                    assertThat(value.toolName()).isEqualTo("get_interview_context");
                    assertThat(value.resourceType()).isEqualTo("INTERVIEW");
                    assertThat(value.resourceId()).isEqualTo(resourceId);
                    assertThat(value.eventType()).isEqualTo("SUCCEEDED");
                    assertThat(value.detail()).isNull();
                    assertThat(value.occurredAt()).isEqualTo(now);
                });

        when(resources.isOwnedBy("INTERVIEW", resourceId, "owner")).thenReturn(false);
        assertThatThrownBy(() -> controller.list(jwt, "INTERVIEW", resourceId))
                .isInstanceOf(ResponseStatusException.class);
    }
}
