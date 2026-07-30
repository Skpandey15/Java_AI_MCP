package com.onlineinterview.mcp.domain;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_approval")
public class McpToolApproval {
    @Id private UUID id;
    @Column(name = "context_id", nullable = false, unique = true) private UUID contextId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpWorkflow workflow;
    @Column(name = "server_key", nullable = false) private String serverKey;
    @Column(name = "tool_name", nullable = false) private String toolName;
    @Column(name = "requester_subject", nullable = false) private String requesterSubject;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "resource_id", nullable = false) private UUID resourceId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpApprovalStatus status;
    @Column(name = "decided_by") private String decidedBy;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;

    protected McpToolApproval() {}

    public static McpToolApproval pending(McpAuthorizationContext context) {
        var value = new McpToolApproval();
        value.id = UUID.randomUUID();
        value.contextId = context.contextId();
        value.workflow = context.workflow();
        value.serverKey = context.serverKey();
        value.toolName = context.toolName();
        value.requesterSubject = context.actorSubject();
        value.resourceType = context.resourceType();
        value.resourceId = context.resourceId();
        value.status = McpApprovalStatus.PENDING;
        value.requestedAt = context.issuedAt();
        value.expiresAt = context.expiresAt();
        return value;
    }

    public void decide(boolean approved, String actor, Instant now) {
        if (status != McpApprovalStatus.PENDING || !expiresAt.isAfter(now)) {
            throw new IllegalStateException("Approval is no longer pending");
        }
        status = approved ? McpApprovalStatus.APPROVED : McpApprovalStatus.REJECTED;
        decidedBy = actor;
        decidedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getContextId() { return contextId; }
    public McpWorkflow getWorkflow() { return workflow; }
    public String getServerKey() { return serverKey; }
    public String getToolName() { return toolName; }
    public String getRequesterSubject() { return requesterSubject; }
    public String getResourceType() { return resourceType; }
    public UUID getResourceId() { return resourceId; }
    public McpApprovalStatus getStatus() { return status; }
    public String getDecidedBy() { return decidedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
