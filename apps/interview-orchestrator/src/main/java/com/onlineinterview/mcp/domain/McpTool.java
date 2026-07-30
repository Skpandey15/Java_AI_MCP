package com.onlineinterview.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mcp_tool")
public class McpTool {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "server_id", nullable = false)
    private McpServer server;
    @Column(name = "tool_name", nullable = false) private String name;
    @Column(nullable = false) private String description;
    @Column(name = "input_schema", nullable = false, columnDefinition = "TEXT")
    private String inputSchema;
    @Column(name = "output_schema", nullable = false, columnDefinition = "TEXT")
    private String outputSchema;
    @Enumerated(EnumType.STRING) @Column(name = "access_type", nullable = false)
    private McpAccessType accessType;
    @Column(name = "candidate_safe", nullable = false) private boolean candidateSafe;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected McpTool() {}

    public UUID getId() { return id; }
    public McpServer getServer() { return server; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getInputSchema() { return inputSchema; }
    public String getOutputSchema() { return outputSchema; }
    public McpAccessType getAccessType() { return accessType; }
    public boolean isCandidateSafe() { return candidateSafe; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
}
