package com.onlineinterview.mcp.transport;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class McpSessionRegistry {
    private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    public McpSessionRegistry() { this(Clock.systemUTC()); }
    McpSessionRegistry(Clock clock) { this.clock = clock; }

    public String create(McpAuthorizationContext context) {
        sessions.entrySet().removeIf(entry ->
                !entry.getValue().context().expiresAt().isAfter(clock.instant()));
        String id = UUID.randomUUID().toString();
        sessions.put(id, new Session(context, false));
        return id;
    }

    public void initialized(String sessionId, McpAuthorizationContext context) {
        Session session = require(sessionId, context);
        sessions.put(sessionId, new Session(session.context(), true));
    }

    public void requireInitialized(String sessionId, McpAuthorizationContext context) {
        if (!require(sessionId, context).initialized()) {
            throw new McpProtocolException(-32002, "MCP session is not initialized");
        }
    }

    public void remove(String sessionId, McpAuthorizationContext context) {
        require(sessionId, context);
        sessions.remove(sessionId);
    }

    private Session require(String sessionId, McpAuthorizationContext context) {
        Session session = sessionId == null ? null : sessions.get(sessionId);
        if (session == null || !session.context().contextId().equals(context.contextId())
                || !session.context().expiresAt().isAfter(clock.instant())) {
            throw new McpProtocolException(-32001, "MCP session is invalid or expired");
        }
        return session;
    }

    record Session(McpAuthorizationContext context, boolean initialized) {}
}
