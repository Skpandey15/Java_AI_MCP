package com.onlineinterview.mcp.application;

import com.onlineinterview.mcp.domain.McpActorRole;
import com.onlineinterview.mcp.domain.McpWorkflow;
import java.time.Instant;
import java.util.UUID;

public record McpAuthorizationContext(
        UUID contextId, McpWorkflow workflow, String serverKey, String toolName,
        String actorSubject, McpActorRole actorRole, String resourceType, UUID resourceId,
        int maxCalls, boolean approvalRequired, Instant issuedAt, Instant expiresAt) {
}
