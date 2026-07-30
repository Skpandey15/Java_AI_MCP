package com.onlineinterview.mcp.api;

import com.onlineinterview.mcp.domain.McpToolApproval;
import java.time.Instant;
import java.util.UUID;

public record McpApprovalResponse(
        UUID id, String workflow, String serverKey, String toolName,
        String resourceType, UUID resourceId, String status,
        Instant requestedAt, Instant expiresAt) {
    static McpApprovalResponse from(McpToolApproval value) {
        return new McpApprovalResponse(value.getId(), value.getWorkflow().name(),
                value.getServerKey(), value.getToolName(), value.getResourceType(),
                value.getResourceId(), value.getStatus().name(),
                value.getRequestedAt(), value.getExpiresAt());
    }
}
