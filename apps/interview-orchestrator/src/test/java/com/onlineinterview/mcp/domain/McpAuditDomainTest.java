package com.onlineinterview.mcp.domain;

import static org.assertj.core.api.Assertions.*;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpAuditDomainTest {
    @Test
    void createsCompleteImmutableAuditSnapshotAndPendingEvaluation() {
        var now = Instant.parse("2026-07-30T10:00:00Z");
        var context = new McpAuthorizationContext(UUID.randomUUID(),
                McpWorkflow.QUESTION_GENERATION, "interview", "get_interview_context",
                "owner", McpActorRole.INTERVIEWER, "INTERVIEW", UUID.randomUUID(),
                5, false, now, now.plusSeconds(60));
        var executionId = UUID.randomUUID();
        var event = McpToolAuditEvent.create(
                executionId, context, McpAuditEventType.FAILED, "timeout", now);

        assertThat(event.getId()).isNotNull();
        assertThat(event.getExecutionId()).isEqualTo(executionId);
        assertThat(event.getContextId()).isEqualTo(context.contextId());
        assertThat(event.getActorSubject()).isEqualTo("owner");
        assertThat(event.getWorkflow()).isEqualTo(McpWorkflow.QUESTION_GENERATION);
        assertThat(event.getServerKey()).isEqualTo("interview");
        assertThat(event.getToolName()).isEqualTo("get_interview_context");
        assertThat(event.getResourceType()).isEqualTo("INTERVIEW");
        assertThat(event.getResourceId()).isEqualTo(context.resourceId());
        assertThat(event.getEventType()).isEqualTo(McpAuditEventType.FAILED);
        assertThat(event.getDetail()).isEqualTo("timeout");
        assertThat(event.getOccurredAt()).isEqualTo(now);

        var evaluation = McpAiEvaluation.pending(
                UUID.randomUUID(), context.contextId(), 7, now);
        assertThat(evaluation.getId()).isNotNull();
        assertThat(evaluation.getSessionId()).isNotNull();
        assertThat(evaluation.getContextId()).isEqualTo(context.contextId());
        assertThat(evaluation.getProposedScore()).isEqualTo(7);
        assertThat(evaluation.getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(evaluation.getCreatedAt()).isEqualTo(now);
        assertThatThrownBy(() -> McpAiEvaluation.pending(
                UUID.randomUUID(), context.contextId(), -1, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
