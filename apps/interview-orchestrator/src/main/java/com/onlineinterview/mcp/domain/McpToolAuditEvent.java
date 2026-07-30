package com.onlineinterview.mcp.domain;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_audit_event")
public class McpToolAuditEvent {
    @Id private UUID id;
    @Column(name = "execution_id", nullable = false) private UUID executionId;
    @Column(name = "context_id", nullable = false) private UUID contextId;
    @Column(name = "actor_subject", nullable = false) private String actorSubject;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpWorkflow workflow;
    @Column(name = "server_key", nullable = false) private String serverKey;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false)
    private McpAuditEventType eventType;
    @Column(length = 500) private String detail;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected McpToolAuditEvent() {}

    public static McpToolAuditEvent create(UUID executionId, McpAuthorizationContext context,
            McpAuditEventType type, String detail, Instant now) {
        var value = new McpToolAuditEvent();
        value.id = UUID.randomUUID();
        value.executionId = executionId;
        value.contextId = context.contextId();
        value.actorSubject = context.actorSubject();
        value.workflow = context.workflow();
        value.serverKey = context.serverKey();
        value.toolName = context.toolName();
        value.resourceType = context.resourceType();
        value.resourceId = context.resourceId();
        value.eventType = type;
        value.detail = detail;
        value.occurredAt = now;
        return value;
    }

    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public UUID getContextId() { return contextId; }
    public String getActorSubject() { return actorSubject; }
    public McpWorkflow getWorkflow() { return workflow; }
    public String getServerKey() { return serverKey; }
    public String getToolName() { return toolName; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public McpAuditEventType getEventType() { return eventType; }
    public String getDetail() { return detail; }
    public Instant getOccurredAt() { return occurredAt; }
}
