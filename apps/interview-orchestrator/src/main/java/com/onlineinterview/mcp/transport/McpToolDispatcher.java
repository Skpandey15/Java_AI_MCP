package com.onlineinterview.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class McpToolDispatcher {
    private final List<McpToolHandler> handlers;

    public McpToolDispatcher(List<McpToolHandler> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    public JsonNode execute(McpAuthorizationContext context, JsonNode arguments) {
        return handlers.stream()
                .filter(handler -> handler.serverKey().equals(context.serverKey())
                        && handler.toolName().equals(context.toolName()))
                .findFirst()
                .orElseThrow(() -> new McpProtocolException(
                        -32601, "MCP tool handler is not implemented"))
                .execute(context, arguments);
    }
}
