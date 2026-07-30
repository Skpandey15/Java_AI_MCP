package com.onlineinterview.mcp.transport;

public class McpProtocolException extends RuntimeException {
    private final int code;

    public McpProtocolException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() { return code; }
}
