package com.onlineinterview.mcp.application;

import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolPolicyRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class McpPolicyService {
    private final McpRegistryService registry;
    private final McpToolPolicyRepository policies;
    private final McpAuthorizationTokenService tokens;
    private final McpApprovalService approvals;
    private final Clock clock;

    @Autowired
    public McpPolicyService(McpRegistryService registry, McpToolPolicyRepository policies,
            McpAuthorizationTokenService tokens, McpApprovalService approvals) {
        this(registry, policies, tokens, approvals, Clock.systemUTC());
    }

    McpPolicyService(McpRegistryService registry, McpToolPolicyRepository policies,
            McpAuthorizationTokenService tokens, McpApprovalService approvals, Clock clock) {
        this.registry = registry;
        this.policies = policies;
        this.tokens = tokens;
        this.approvals = approvals;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AuthorizedTool authorize(McpWorkflow workflow, String serverKey, String toolName,
            String actorSubject, McpActorRole actorRole, String resourceType, UUID resourceId) {
        if (actorSubject == null || actorSubject.isBlank()
                || resourceType == null || !resourceType.matches("[A-Z_]{3,40}")
                || resourceId == null) {
            throw new IllegalArgumentException("Actor and target resource are required");
        }
        var tool = registry.resolve(serverKey, toolName);
        var policy = policies.findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
                        workflow, tool.toolId(), actorRole)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "MCP tool is not allowed for this workflow"));
        if (actorRole == McpActorRole.CANDIDATE && !tool.candidateSafe()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "MCP tool is not candidate safe");
        }
        if (tool.accessType() == McpAccessType.STATE_CHANGING
                && !policy.isApprovalRequired()) {
            throw new IllegalStateException(
                    "State-changing MCP tools must require approval");
        }
        var issuedAt = clock.instant();
        var context = new McpAuthorizationContext(UUID.randomUUID(), workflow,
                serverKey, toolName, actorSubject, actorRole, resourceType, resourceId,
                policy.getMaxCalls(), policy.isApprovalRequired(), issuedAt,
                issuedAt.plusSeconds(policy.getTtlSeconds()));
        if (context.approvalRequired()) approvals.request(context);
        return new AuthorizedTool(tool, context, tokens.issue(context));
    }

    public record AuthorizedTool(McpRegistryService.ToolDefinition tool,
            McpAuthorizationContext context, String authorizationToken) {}
}
