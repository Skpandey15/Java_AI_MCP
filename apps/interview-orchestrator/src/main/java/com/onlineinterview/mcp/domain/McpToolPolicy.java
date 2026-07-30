package com.onlineinterview.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_policy")
public class McpToolPolicy {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpWorkflow workflow;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tool_id", nullable = false)
    private McpTool tool;
    @Enumerated(EnumType.STRING) @Column(name = "actor_role", nullable = false)
    private McpActorRole actorRole;
    @Column(name = "approval_required", nullable = false) private boolean approvalRequired;
    @Column(name = "max_calls", nullable = false) private int maxCalls;
    @Column(name = "authorization_ttl_seconds", nullable = false) private int ttlSeconds;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected McpToolPolicy() {}

    public UUID getId() { return id; }
    public McpWorkflow getWorkflow() { return workflow; }
    public McpTool getTool() { return tool; }
    public McpActorRole getActorRole() { return actorRole; }
    public boolean isApprovalRequired() { return approvalRequired; }
    public int getMaxCalls() { return maxCalls; }
    public int getTtlSeconds() { return ttlSeconds; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
