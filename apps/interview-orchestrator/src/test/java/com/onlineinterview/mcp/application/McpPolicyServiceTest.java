package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolPolicyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class McpPolicyServiceTest {
    private final McpRegistryService registry = mock(McpRegistryService.class);
    private final McpToolPolicyRepository policies = mock(McpToolPolicyRepository.class);
    private final McpAuthorizationTokenService tokens = mock(McpAuthorizationTokenService.class);
    private final McpApprovalService approvals = mock(McpApprovalService.class);
    private final Instant now = Instant.parse("2026-07-30T10:00:00Z");
    private final McpPolicyService service = new McpPolicyService(
            registry, policies, tokens, approvals, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void issuesContextFromExactWorkflowRoleAndToolPolicy() {
        var tool = tool(McpAccessType.READ_ONLY, false);
        var policy = mock(McpToolPolicy.class);
        when(registry.resolve("knowledge", "search_knowledge")).thenReturn(tool);
        when(policies.findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
                McpWorkflow.QUESTION_GENERATION, tool.toolId(), McpActorRole.INTERVIEWER))
                .thenReturn(Optional.of(policy));
        when(policy.getMaxCalls()).thenReturn(7);
        when(policy.getTtlSeconds()).thenReturn(90);
        when(tokens.issue(any())).thenReturn("signed-token");
        UUID interviewId = UUID.randomUUID();

        var authorized = service.authorize(McpWorkflow.QUESTION_GENERATION,
                "knowledge", "search_knowledge", "actor-1", McpActorRole.INTERVIEWER,
                "INTERVIEW", interviewId);

        assertThat(authorized.authorizationToken()).isEqualTo("signed-token");
        assertThat(authorized.context().actorSubject()).isEqualTo("actor-1");
        assertThat(authorized.context().resourceId()).isEqualTo(interviewId);
        assertThat(authorized.context().maxCalls()).isEqualTo(7);
        assertThat(authorized.context().expiresAt()).isEqualTo(now.plusSeconds(90));
    }

    @Test
    void deniesMissingPolicyUnsafeCandidateAndUnsafeStatePolicy() {
        var readTool = tool(McpAccessType.READ_ONLY, false);
        when(registry.resolve(anyString(), anyString())).thenReturn(readTool);
        when(policies.findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
                any(), any(), any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> authorize(McpActorRole.INTERVIEWER))
                .isInstanceOf(ResponseStatusException.class);

        var candidatePolicy = mock(McpToolPolicy.class);
        when(policies.findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
                any(), any(), eq(McpActorRole.CANDIDATE)))
                .thenReturn(Optional.of(candidatePolicy));
        assertThatThrownBy(() -> authorize(McpActorRole.CANDIDATE))
                .isInstanceOf(ResponseStatusException.class);

        var stateTool = tool(McpAccessType.STATE_CHANGING, false);
        var statePolicy = mock(McpToolPolicy.class);
        when(registry.resolve(anyString(), anyString())).thenReturn(stateTool);
        when(policies.findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
                any(), eq(stateTool.toolId()), eq(McpActorRole.SERVICE)))
                .thenReturn(Optional.of(statePolicy));
        when(statePolicy.isApprovalRequired()).thenReturn(false);
        assertThatThrownBy(() -> authorize(McpActorRole.SERVICE))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMissingActorAndMalformedTargetType() {
        assertThatThrownBy(() -> service.authorize(McpWorkflow.QUESTION_GENERATION,
                "knowledge", "search", "", McpActorRole.INTERVIEWER,
                "interview", UUID.randomUUID())).isInstanceOf(IllegalArgumentException.class);
    }

    private McpPolicyService.AuthorizedTool authorize(McpActorRole role) {
        return service.authorize(McpWorkflow.QUESTION_GENERATION,
                "knowledge", "search_knowledge", "actor", role,
                "INTERVIEW", UUID.randomUUID());
    }

    private static McpRegistryService.ToolDefinition tool(
            McpAccessType access, boolean candidateSafe) {
        var schema = new ObjectMapper().createObjectNode();
        return new McpRegistryService.ToolDefinition(UUID.randomUUID(), "knowledge",
                "http://knowledge", "search_knowledge", "Search", schema, schema,
                access, candidateSafe);
    }
}
