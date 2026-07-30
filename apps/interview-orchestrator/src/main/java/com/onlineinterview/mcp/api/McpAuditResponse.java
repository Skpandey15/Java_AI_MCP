package com.onlineinterview.mcp.api;

import com.onlineinterview.mcp.domain.McpToolAuditEvent;
import java.time.Instant;
import java.util.UUID;

public record McpAuditResponse(
        UUID id, UUID executionId, UUID contextId, String workflow, String serverKey,
        String toolName, String resourceType, UUID resourceId, String eventType,
        String detail, Instant occurredAt) {
    static McpAuditResponse from(McpToolAuditEvent value) {
        return new McpAuditResponse(value.getId(), value.getExecutionId(), value.getContextId(),
                value.getWorkflow().name(), value.getServerKey(), value.getToolName(),
                value.getResourceType(), value.getResourceId(), value.getEventType().name(),
                value.getDetail(), value.getOccurredAt());
    }
}
