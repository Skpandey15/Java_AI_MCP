package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.mcp.domain.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpSessionRegistryTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private final McpSessionRegistry sessions =
            new McpSessionRegistry(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void requiresMatchingInitializedUnexpiredSession() {
        var context = context(NOW.plusSeconds(60));
        String id = sessions.create(context);
        assertThatThrownBy(() -> sessions.requireInitialized(id, context))
                .isInstanceOf(McpProtocolException.class);
        sessions.initialized(id, context);
        sessions.requireInitialized(id, context);
        assertThatThrownBy(() -> sessions.requireInitialized(id, context(NOW.plusSeconds(60))))
                .isInstanceOf(McpProtocolException.class);
        sessions.remove(id, context);
        assertThatThrownBy(() -> sessions.requireInitialized(id, context))
                .isInstanceOf(McpProtocolException.class);
    }

    @Test
    void rejectsAndCleansExpiredSessions() {
        var expired = context(NOW);
        String id = sessions.create(expired);
        assertThatThrownBy(() -> sessions.initialized(id, expired))
                .isInstanceOf(McpProtocolException.class);
        sessions.create(context(NOW.plusSeconds(60)));
    }

    private static McpAuthorizationContext context(Instant expires) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.QUESTION_GENERATION,
                "knowledge", "search_knowledge", "actor", McpActorRole.INTERVIEWER,
                "INTERVIEW", UUID.randomUUID(), 2, false, NOW, expires);
    }
}
