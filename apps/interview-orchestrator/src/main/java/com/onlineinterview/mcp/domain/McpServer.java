package com.onlineinterview.mcp.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "mcp_server")
public class McpServer {
    @Id private UUID id;
    @Column(name = "server_key", nullable = false, unique = true) private String key;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "base_url", nullable = false) private String baseUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpTransport transport;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private McpClassification classification;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "server", fetch = FetchType.EAGER)
    @OrderBy("name ASC")
    private List<McpTool> tools = new ArrayList<>();

    protected McpServer() {}

    public UUID getId() { return id; }
    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public String getBaseUrl() { return baseUrl; }
    public McpTransport getTransport() { return transport; }
    public McpClassification getClassification() { return classification; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public List<McpTool> getTools() { return List.copyOf(tools); }
}
