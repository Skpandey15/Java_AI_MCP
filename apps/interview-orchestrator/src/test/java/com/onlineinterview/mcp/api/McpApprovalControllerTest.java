package com.onlineinterview.mcp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlineinterview.mcp.application.McpApprovalService;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.mcp.domain.McpActorRole;
import com.onlineinterview.mcp.domain.McpToolApproval;
import com.onlineinterview.mcp.domain.McpWorkflow;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class McpApprovalControllerTest {
    private final McpApprovalService approvals = mock(McpApprovalService.class);
    private final McpApprovalController controller = new McpApprovalController(approvals);
    private final Jwt jwt = mock(Jwt.class);

    @Test
    void listsPendingApprovalsAndMapsResponse() {
        var approval = approval();
        when(jwt.getSubject()).thenReturn("owner");
        when(approvals.pending("owner")).thenReturn(List.of(approval));

        var result = controller.pending(jwt);

        assertThat(result).singleElement().satisfies(value -> {
            assertThat(value.id()).isEqualTo(approval.getId());
            assertThat(value.workflow()).isEqualTo("ANSWER_EVALUATION");
            assertThat(value.serverKey()).isEqualTo("result");
            assertThat(value.toolName()).isEqualTo("submit_ai_evaluation");
            assertThat(value.resourceType()).isEqualTo("SESSION");
            assertThat(value.resourceId()).isEqualTo(approval.getResourceId());
            assertThat(value.status()).isEqualTo("PENDING");
            assertThat(value.requestedAt()).isEqualTo(approval.getRequestedAt());
            assertThat(value.expiresAt()).isEqualTo(approval.getExpiresAt());
        });
    }

    @Test
    void recordsApprovalDecisionForAuthenticatedOwner() {
        var approval = approval();
        when(jwt.getSubject()).thenReturn("owner");
        when(approvals.decide(approval.getId(), "owner", true)).thenReturn(approval);

        var result = controller.decide(
                jwt, approval.getId(), new McpApprovalDecisionRequest(true));

        assertThat(result.id()).isEqualTo(approval.getId());
        verify(approvals).decide(approval.getId(), "owner", true);
    }

    private static McpToolApproval approval() {
        var now = Instant.parse("2026-07-30T10:00:00Z");
        return McpToolApproval.pending(new McpAuthorizationContext(
                UUID.randomUUID(), McpWorkflow.ANSWER_EVALUATION, "result",
                "submit_ai_evaluation", "owner", McpActorRole.INTERVIEWER,
                "SESSION", UUID.randomUUID(), 1, true, now, now.plusSeconds(60)));
    }
}
