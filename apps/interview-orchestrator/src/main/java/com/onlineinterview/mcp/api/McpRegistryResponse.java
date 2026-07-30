package com.onlineinterview.mcp.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlineinterview.mcp.application.McpRegistryService.ServerDefinition;
import com.onlineinterview.mcp.application.McpRegistryService.ToolDefinition;
import java.util.List;

public record McpRegistryResponse(
        String key, String displayName, String transport, String classification,
        List<ToolResponse> tools) {
    static McpRegistryResponse from(ServerDefinition server) {
        return new McpRegistryResponse(server.key(), server.displayName(),
                server.transport().name(), server.classification().name(),
                server.tools().stream().map(ToolResponse::from).toList());
    }

    public record ToolResponse(String name, String description, JsonNode inputSchema,
            JsonNode outputSchema, String accessType, boolean candidateSafe) {
        static ToolResponse from(ToolDefinition tool) {
            return new ToolResponse(tool.name(), tool.description(), tool.inputSchema(),
                    tool.outputSchema(), tool.accessType().name(), tool.candidateSafe());
        }
    }
}
