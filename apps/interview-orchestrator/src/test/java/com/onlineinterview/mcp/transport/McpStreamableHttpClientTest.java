package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onlineinterview.mcp.application.*;
import com.onlineinterview.mcp.domain.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class McpStreamableHttpClientTest {
    @Test
    void negotiatesCallsSseToolAndClosesSession() throws Exception {
        var mapper = new ObjectMapper();
        var step = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", exchange -> {
            if ("DELETE".equals(exchange.getRequestMethod())) {
                assertThat(exchange.getRequestHeaders().getFirst(McpProtocol.SESSION_HEADER))
                        .isEqualTo("session-1");
                step.incrementAndGet();
                exchange.sendResponseHeaders(204, -1);
                exchange.close();
                return;
            }
            var body = mapper.readTree(exchange.getRequestBody());
            assertThat(exchange.getRequestHeaders().getFirst("Accept"))
                    .contains("application/json", "text/event-stream");
            assertThat(exchange.getRequestHeaders().getFirst(
                    McpProtocol.AUTHORIZATION_HEADER)).isEqualTo("signed");
            String method = body.path("method").asText();
            if ("initialize".equals(method)) {
                assertThat(body.path("params").path("protocolVersion").asText())
                        .isEqualTo(McpProtocol.VERSION);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.getResponseHeaders().add(McpProtocol.SESSION_HEADER, "session-1");
                respond(exchange, 200, """
                        {"jsonrpc":"2.0","id":"%s","result":{
                         "protocolVersion":"%s","capabilities":{"tools":{}},
                         "serverInfo":{"name":"test","version":"1"}}}
                        """.formatted(body.path("id").asText(), McpProtocol.VERSION));
            } else if ("notifications/initialized".equals(method)) {
                assertThat(exchange.getRequestHeaders().getFirst(McpProtocol.SESSION_HEADER))
                        .isEqualTo("session-1");
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            } else {
                assertThat(method).isEqualTo("tools/call");
                assertThat(exchange.getRequestHeaders().getFirst(McpProtocol.VERSION_HEADER))
                        .isEqualTo(McpProtocol.VERSION);
                exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
                respond(exchange, 200,
                        "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":\""
                        + body.path("id").asText()
                        + "\",\"result\":{\"content\":[],\"structuredContent\":"
                        + "{\"accepted\":true},\"isError\":false}}\n\n");
            }
        });
        server.start();
        try {
            var client = new McpStreamableHttpClient(RestClient.builder(), mapper,
                    new McpPayloadValidator());
            var authorized = authorized("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/mcp", mapper);

            var output = client.execute(authorized,
                    mapper.createObjectNode().put("query", "Java"));

            assertThat(output.path("accepted").asBoolean()).isTrue();
            assertThat(step.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    private static McpPolicyService.AuthorizedTool authorized(
            String endpoint, ObjectMapper mapper) {
        ObjectNode input = schema(mapper, "query", "string");
        ObjectNode output = schema(mapper, "accepted", "boolean");
        var tool = new McpRegistryService.ToolDefinition(UUID.randomUUID(), "knowledge",
                endpoint, "search_knowledge", "Search", input, output,
                McpAccessType.READ_ONLY, false);
        var context = new McpAuthorizationContext(UUID.randomUUID(),
                McpWorkflow.QUESTION_GENERATION, "knowledge", "search_knowledge",
                "actor", McpActorRole.INTERVIEWER, "INTERVIEW", UUID.randomUUID(),
                2, false, Instant.now(), Instant.now().plusSeconds(60));
        return new McpPolicyService.AuthorizedTool(tool, context, "signed");
    }

    private static ObjectNode schema(ObjectMapper mapper, String property, String type) {
        ObjectNode value = mapper.createObjectNode().put("type", "object");
        value.set("properties", mapper.valueToTree(
                java.util.Map.of(property, java.util.Map.of("type", type))));
        value.set("required", mapper.valueToTree(java.util.List.of(property)));
        value.put("additionalProperties", false);
        return value;
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
