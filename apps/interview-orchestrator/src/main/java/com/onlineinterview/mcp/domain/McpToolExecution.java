package com.onlineinterview.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool_execution")
public class McpToolExecution {
    @Id private UUID id;
    @Column(name = "context_id", nullable = false) private UUID contextId;
    @Column(name = "idempotency_key") private String idempotencyKey;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpExecutionStatus status;
    @Column(name = "result_json", columnDefinition = "TEXT") private String resultJson;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected McpToolExecution() {}

    public static McpToolExecution started(UUID contextId, String key, Instant now) {
        var value = new McpToolExecution();
        value.id = UUID.randomUUID();
        value.contextId = contextId;
        value.idempotencyKey = key;
        value.status = McpExecutionStatus.IN_PROGRESS;
        value.startedAt = now;
        return value;
    }

    public void succeed(String result, Instant now) {
        status = McpExecutionStatus.SUCCEEDED;
        resultJson = result;
        completedAt = now;
    }

    public void fail(Instant now) {
        status = McpExecutionStatus.FAILED;
        completedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getContextId() { return contextId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public McpExecutionStatus getStatus() { return status; }
    public String getResultJson() { return resultJson; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
