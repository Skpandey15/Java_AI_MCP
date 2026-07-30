package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onlineinterview.mcp.application.*;
import com.onlineinterview.mcp.domain.*;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class McpHostControllerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpAuthorizationTokenService tokens = mock(McpAuthorizationTokenService.class);
    private final McpRegistryService registry = mock(McpRegistryService.class);
    private final McpControlledExecutionService execution =
            mock(McpControlledExecutionService.class);
    private final McpHostController host = new McpHostController(mapper, tokens,
            new McpSessionRegistry(), registry, new McpPayloadValidator(), execution);
    private final McpAuthorizationContext context = context(false);
    private final McpRegistryService.ToolDefinition tool = tool();

    @BeforeEach
    void setup() {
        when(tokens.verify("token")).thenReturn(context);
        when(registry.resolve("knowledge", "search_knowledge")).thenReturn(tool);
    }

    @Test
    void negotiatesListsAndExecutesStructuredToolResult() {
        var http = new MockHttpServletRequest();
        ObjectNode initialize = request("1", "initialize");
        initialize.set("params", mapper.valueToTree(
                java.util.Map.of("protocolVersion", McpProtocol.VERSION)));

        var initialized = host.post("knowledge", "token", null, null, initialize, http);
        String session = initialized.getHeaders().getFirst(McpProtocol.SESSION_HEADER);
        assertThat(initialized.getBody().path("result").path("protocolVersion").asText())
                .isEqualTo(McpProtocol.VERSION);

        var notification = request(null, "notifications/initialized");
        assertThat(host.post("knowledge", "token", session, McpProtocol.VERSION,
                notification, http).getStatusCode().value()).isEqualTo(202);

        var list = host.post("knowledge", "token", session, McpProtocol.VERSION,
                request("2", "tools/list"), http);
        assertThat(list.getBody().path("result").path("tools").get(0).path("name").asText())
                .isEqualTo("search_knowledge");

        ObjectNode arguments = mapper.createObjectNode().put("query", "Java");
        ObjectNode call = request("3", "tools/call");
        call.set("params", mapper.valueToTree(
                java.util.Map.of("name", "search_knowledge", "arguments", arguments)));
        when(execution.execute(context, McpAccessType.READ_ONLY, arguments, null))
                .thenReturn(mapper.createObjectNode().put("accepted", true));
        var result = host.post("knowledge", "token", session, McpProtocol.VERSION,
                call, http);

        assertThat(result.getBody().path("result").path("structuredContent")
                .path("accepted").asBoolean()).isTrue();
        assertThat(host.close("token", session).getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void returnsProtocolErrorsForInvalidScopeVersionInputApprovalAndOrigin() {
        var http = new MockHttpServletRequest();
        assertError(host.post("other", "token", null, null,
                request("1", "initialize"), http), -32003);
        assertError(host.post("knowledge", "token", null, null,
                mapper.createObjectNode(), http), -32600);

        ObjectNode badVersion = request("1", "initialize");
        badVersion.set("params", mapper.valueToTree(
                java.util.Map.of("protocolVersion", "old")));
        assertError(host.post("knowledge", "token", null, null,
                badVersion, http), -32602);

        http.addHeader("Origin", "https://browser.example");
        var originResponse = host.post("knowledge", "token", null, null,
                badVersion, http);
        assertError(originResponse, -32003);
        assertThat(originResponse.getStatusCode().value()).isEqualTo(403);

        var approvedContext = context(true);
        when(tokens.verify("approval")).thenReturn(approvedContext);
        String session = initialize("approval", approvedContext);
        ObjectNode call = request("3", "tools/call");
        call.set("params", mapper.valueToTree(java.util.Map.of(
                "name", "search_knowledge",
                "arguments", java.util.Map.of("query", "Java"))));
        when(execution.execute(eq(approvedContext), eq(McpAccessType.READ_ONLY),
                any(), isNull())).thenThrow(
                        new McpApprovalService.McpProtocolApprovalException("approval"));
        assertError(host.post("knowledge", "approval", session, McpProtocol.VERSION,
                call, new MockHttpServletRequest()), -32004);
    }

    @Test
    void rejectsInvalidAuthorizationWithoutLeakingReason() {
        when(tokens.verify("bad")).thenThrow(new IllegalArgumentException("signature"));
        var response = host.post("knowledge", "bad", null, null,
                request("1", "initialize"), new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(response.getBody().path("error").path("message").asText())
                .isEqualTo("Invalid MCP authorization");
    }

    private String initialize(String token, McpAuthorizationContext expected) {
        ObjectNode initialize = request("1", "initialize");
        initialize.set("params", mapper.valueToTree(
                java.util.Map.of("protocolVersion", McpProtocol.VERSION)));
        var response = host.post("knowledge", token, null, null, initialize,
                new MockHttpServletRequest());
        String session = response.getHeaders().getFirst(McpProtocol.SESSION_HEADER);
        host.post("knowledge", token, session, McpProtocol.VERSION,
                request(null, "notifications/initialized"), new MockHttpServletRequest());
        return session;
    }

    private void assertError(org.springframework.http.ResponseEntity<com.fasterxml.jackson.databind.JsonNode> response,
            int code) {
        assertThat(response.getBody().path("error").path("code").asInt()).isEqualTo(code);
    }

    private ObjectNode request(String id, String method) {
        ObjectNode value = mapper.createObjectNode().put("jsonrpc", "2.0");
        if (id != null) value.put("id", id);
        value.put("method", method);
        return value;
    }

    private static McpAuthorizationContext context(boolean approval) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.QUESTION_GENERATION,
                "knowledge", "search_knowledge", "actor", McpActorRole.INTERVIEWER,
                "INTERVIEW", UUID.randomUUID(), 2, approval,
                Instant.now(), Instant.now().plusSeconds(120));
    }

    private McpRegistryService.ToolDefinition tool() {
        ObjectNode input = mapper.createObjectNode().put("type", "object");
        input.set("properties", mapper.valueToTree(
                java.util.Map.of("query", java.util.Map.of("type", "string"))));
        input.set("required", mapper.valueToTree(java.util.List.of("query")));
        input.put("additionalProperties", false);
        ObjectNode output = mapper.createObjectNode().put("type", "object");
        output.set("properties", mapper.valueToTree(
                java.util.Map.of("accepted", java.util.Map.of("type", "boolean"))));
        output.set("required", mapper.valueToTree(java.util.List.of("accepted")));
        output.put("additionalProperties", false);
        return new McpRegistryService.ToolDefinition(UUID.randomUUID(), "knowledge",
                "http://knowledge", "search_knowledge", "Search", input, output,
                McpAccessType.READ_ONLY, false);
    }
}
