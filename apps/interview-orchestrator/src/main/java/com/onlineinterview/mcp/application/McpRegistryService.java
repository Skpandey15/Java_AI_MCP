package com.onlineinterview.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlineinterview.mcp.domain.McpAccessType;
import com.onlineinterview.mcp.domain.McpClassification;
import com.onlineinterview.mcp.domain.McpTransport;
import com.onlineinterview.mcp.infrastructure.McpServerRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class McpRegistryService {
    private final McpServerRepository servers;
    private final McpSchemaValidator schemas;

    public McpRegistryService(McpServerRepository servers, McpSchemaValidator schemas) {
        this.servers = servers;
        this.schemas = schemas;
    }

    @Transactional(readOnly = true)
    public List<ServerDefinition> listEnabled() {
        return servers.findByEnabledTrueOrderByKeyAsc().stream().map(server ->
                new ServerDefinition(server.getKey(), server.getDisplayName(),
                        server.getTransport(), server.getClassification(),
                        server.getTools().stream().filter(tool -> tool.isEnabled())
                                .map(tool -> definition(server.getKey(), server.getBaseUrl(), tool))
                                .toList())).toList();
    }

    @Transactional(readOnly = true)
    public ToolDefinition resolve(String serverKey, String toolName) {
        var server = servers.findByKeyAndEnabledTrue(serverKey)
                .orElseThrow(() -> notFound(serverKey, toolName));
        return server.getTools().stream()
                .filter(tool -> tool.isEnabled() && tool.getName().equals(toolName))
                .findFirst()
                .map(tool -> definition(server.getKey(), server.getBaseUrl(), tool))
                .orElseThrow(() -> notFound(serverKey, toolName));
    }

    private ToolDefinition definition(String serverKey, String baseUrl,
            com.onlineinterview.mcp.domain.McpTool tool) {
        return new ToolDefinition(tool.getId(), serverKey, baseUrl, tool.getName(), tool.getDescription(),
                schemas.validate(tool.getInputSchema()), schemas.validate(tool.getOutputSchema()),
                tool.getAccessType(), tool.isCandidateSafe());
    }

    private ResponseStatusException notFound(String serverKey, String toolName) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Enabled MCP tool not found: " + serverKey + "/" + toolName);
    }

    public record ServerDefinition(String key, String displayName, McpTransport transport,
            McpClassification classification, List<ToolDefinition> tools) {}
    public record ToolDefinition(java.util.UUID toolId, String serverKey, String baseUrl, String name,
            String description, JsonNode inputSchema, JsonNode outputSchema,
            McpAccessType accessType, boolean candidateSafe) {}
}
