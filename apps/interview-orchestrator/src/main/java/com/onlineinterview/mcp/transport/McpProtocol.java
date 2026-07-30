package com.onlineinterview.mcp.transport;

public final class McpProtocol {
    public static final String VERSION = "2025-11-25";
    public static final String SESSION_HEADER = "Mcp-Session-Id";
    public static final String VERSION_HEADER = "MCP-Protocol-Version";
    public static final String AUTHORIZATION_HEADER = "X-MCP-Authorization";
    public static final String ACCEPT = "application/json, text/event-stream";

    private McpProtocol() {}
}
