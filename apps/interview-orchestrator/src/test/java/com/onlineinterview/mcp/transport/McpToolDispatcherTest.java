package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.*;

import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.application.McpAuthorizationContext;
import com.onlineinterview.mcp.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpToolDispatcherTest {
    @Test
    void dispatchesOnlyExactServerAndTool() {
        var mapper = new ObjectMapper();
        McpToolHandler handler = new McpToolHandler() {
            public String serverKey() { return "knowledge"; }
            public String toolName() { return "search_knowledge"; }
            public tools.jackson.databind.JsonNode execute(
                    McpAuthorizationContext context,
                    tools.jackson.databind.JsonNode arguments) {
                return mapper.createObjectNode().put("accepted", true);
            }
        };
        var dispatcher = new McpToolDispatcher(List.of(handler));
        assertThat(dispatcher.execute(context("knowledge", "search_knowledge"),
                mapper.createObjectNode()).path("accepted").asBoolean()).isTrue();
        assertThatThrownBy(() -> dispatcher.execute(context("other", "search_knowledge"),
                mapper.createObjectNode())).isInstanceOf(McpProtocolException.class);
    }

    private static McpAuthorizationContext context(String server, String tool) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.QUESTION_GENERATION,
                server, tool, "actor", McpActorRole.INTERVIEWER, "INTERVIEW",
                UUID.randomUUID(), 2, false, Instant.now(), Instant.now().plusSeconds(60));
    }
}
