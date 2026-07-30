package com.onlineinterview.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.onlineinterview.mcp.application.McpAuthorizationContext;

public interface McpToolHandler {
    String serverKey();
    String toolName();
    JsonNode execute(McpAuthorizationContext context, JsonNode arguments);
}
