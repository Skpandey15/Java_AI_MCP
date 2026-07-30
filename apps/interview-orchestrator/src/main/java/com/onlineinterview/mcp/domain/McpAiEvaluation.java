package com.onlineinterview.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_ai_evaluation")
public class McpAiEvaluation {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "context_id", nullable = false, unique = true) private UUID contextId;
    @Column(name = "proposed_score", nullable = false) private int proposedScore;
    @Column(nullable = false) private String status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected McpAiEvaluation() {}

    public static McpAiEvaluation pending(
            UUID sessionId, UUID contextId, int proposedScore, Instant now) {
        if (proposedScore < 0) throw new IllegalArgumentException("Score cannot be negative");
        var value = new McpAiEvaluation();
        value.id = UUID.randomUUID();
        value.sessionId = sessionId;
        value.contextId = contextId;
        value.proposedScore = proposedScore;
        value.status = "PENDING_REVIEW";
        value.createdAt = now;
        return value;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public UUID getContextId() { return contextId; }
    public int getProposedScore() { return proposedScore; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
