package com.onlineinterview.mcp.domain;

import static org.assertj.core.api.Assertions.*;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpExecutionDomainTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void approvalTransitionsOnceBeforeExpiry() {
        var context = context(true, NOW.plusSeconds(60));
        var approval = McpToolApproval.pending(context);

        approval.decide(true, "owner", NOW.plusSeconds(1));

        assertThat(approval.getId()).isNotNull();
        assertThat(approval.getContextId()).isEqualTo(context.contextId());
        assertThat(approval.getWorkflow()).isEqualTo(context.workflow());
        assertThat(approval.getServerKey()).isEqualTo("result");
        assertThat(approval.getToolName()).isEqualTo("submit_ai_evaluation");
        assertThat(approval.getRequesterSubject()).isEqualTo("service");
        assertThat(approval.getResourceType()).isEqualTo("SESSION");
        assertThat(approval.getResourceId()).isEqualTo(context.resourceId());
        assertThat(approval.getStatus()).isEqualTo(McpApprovalStatus.APPROVED);
        assertThat(approval.getDecidedBy()).isEqualTo("owner");
        assertThat(approval.getRequestedAt()).isEqualTo(NOW);
        assertThat(approval.getDecidedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(approval.getExpiresAt()).isEqualTo(NOW.plusSeconds(60));
        assertThatThrownBy(() -> approval.decide(false, "owner", NOW.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void executionTracksSuccessAndFailureWithoutArguments() {
        UUID contextId = UUID.randomUUID();
        var success = McpToolExecution.started(contextId, "key", NOW);
        success.succeed("{\"accepted\":true}", NOW.plusSeconds(1));
        assertThat(success.getId()).isNotNull();
        assertThat(success.getContextId()).isEqualTo(contextId);
        assertThat(success.getIdempotencyKey()).isEqualTo("key");
        assertThat(success.getStatus()).isEqualTo(McpExecutionStatus.SUCCEEDED);
        assertThat(success.getResultJson()).contains("accepted");
        assertThat(success.getStartedAt()).isEqualTo(NOW);
        assertThat(success.getCompletedAt()).isEqualTo(NOW.plusSeconds(1));

        var failed = McpToolExecution.started(contextId, null, NOW);
        failed.fail(NOW.plusSeconds(2));
        assertThat(failed.getStatus()).isEqualTo(McpExecutionStatus.FAILED);
        assertThat(failed.getCompletedAt()).isEqualTo(NOW.plusSeconds(2));
    }

    private static McpAuthorizationContext context(boolean approval, Instant expiry) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.ANSWER_EVALUATION,
                "result", "submit_ai_evaluation", "service", McpActorRole.SERVICE,
                "SESSION", UUID.randomUUID(), 1, approval, NOW, expiry);
    }
}
