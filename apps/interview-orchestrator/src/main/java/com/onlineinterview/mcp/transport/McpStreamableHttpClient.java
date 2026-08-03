package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.onlineinterview.mcp.application.McpPolicyService.AuthorizedTool;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class McpStreamableHttpClient {
    static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final RestClient client;
    private final ObjectMapper mapper;
    private final McpPayloadValidator payloads;

    @Autowired
    public McpStreamableHttpClient(
            RestClient.Builder builder, ObjectMapper mapper, McpPayloadValidator payloads) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .version(HttpClient.Version.HTTP_1_1).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.client = builder.requestFactory(factory).build();
        this.mapper = mapper;
        this.payloads = payloads;
    }

    McpStreamableHttpClient(
            RestClient client, ObjectMapper mapper, McpPayloadValidator payloads) {
        this.client = client;
        this.mapper = mapper;
        this.payloads = payloads;
    }

    public JsonNode execute(AuthorizedTool authorization, JsonNode arguments) {
        String token = authorization.authorizationToken();
        String endpoint = authorization.tool().baseUrl();
        String initializeId = UUID.randomUUID().toString();
        ObjectNode initialize = request(initializeId, "initialize");
        initialize.set("params", mapper.valueToTree(java.util.Map.of(
                "protocolVersion", McpProtocol.VERSION,
                "capabilities", java.util.Map.of(),
                "clientInfo", java.util.Map.of(
                        "name", "online-interview-orchestrator", "version", "0.1.0"))));
        var initialized = post(endpoint, token, null, null, null, initialize);
        JsonNode initializeResult = result(initialized.body(), initializeId);
        if (!McpProtocol.VERSION.equals(initializeResult.path("protocolVersion").asText())) {
            throw new McpProtocolException(-32005, "MCP protocol negotiation failed");
        }
        String sessionId = initialized.headers().getFirst(McpProtocol.SESSION_HEADER);
        try {
            ObjectNode notification = mapper.createObjectNode();
            notification.put("jsonrpc", "2.0");
            notification.put("method", "notifications/initialized");
            notification.set("params", mapper.createObjectNode());
            var accepted = post(endpoint, token, sessionId, McpProtocol.VERSION,
                    null, notification);
            if (accepted.status() != HttpStatus.ACCEPTED.value()) {
                throw new McpProtocolException(-32005, "MCP initialization notification failed");
            }

            String callId = UUID.randomUUID().toString();
            ObjectNode call = request(callId, "tools/call");
            ObjectNode params = mapper.createObjectNode();
            params.put("name", authorization.tool().name());
            params.set("arguments", arguments);
            call.set("params", params);
            String idempotencyKey = authorization.tool().accessType()
                    == com.onlineinterview.mcp.domain.McpAccessType.STATE_CHANGING
                    ? authorization.context().contextId().toString() : null;
            JsonNode callResult = result(post(endpoint, token, sessionId,
                    McpProtocol.VERSION, idempotencyKey, call).body(), callId);
            if (callResult.path("isError").asBoolean(false)) {
                throw new McpProtocolException(-32006, "MCP tool execution failed");
            }
            JsonNode output = callResult.path("structuredContent");
            payloads.validate(output, authorization.tool().outputSchema());
            return output;
        } finally {
            close(endpoint, token, sessionId);
        }
    }

    private TransportResponse post(String endpoint, String token, String sessionId,
            String version, String idempotencyKey, JsonNode body) {
        return client.post().uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.ACCEPT, McpProtocol.ACCEPT)
                .header(McpProtocol.AUTHORIZATION_HEADER, token)
                .headers(headers -> {
                    if (sessionId != null) headers.set(McpProtocol.SESSION_HEADER, sessionId);
                    if (version != null) headers.set(McpProtocol.VERSION_HEADER, version);
                    if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
                })
                .body(body)
                .exchange((request, response) -> {
                    String raw = new String(response.getBody().readAllBytes(),
                            java.nio.charset.StandardCharsets.UTF_8);
                    if (raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                            > MAX_RESPONSE_BYTES) {
                        throw new McpProtocolException(-32007, "MCP response exceeds 1 MiB");
                    }
                    JsonNode parsed = raw.isBlank() ? null : parseBody(raw,
                            response.getHeaders().getContentType());
                    return new TransportResponse(response.getStatusCode().value(),
                            response.getHeaders(), parsed);
                });
    }

    private JsonNode parseBody(String raw, MediaType contentType) {
        try {
            String json = raw;
            if (contentType != null && MediaType.TEXT_EVENT_STREAM.isCompatibleWith(contentType)) {
                json = raw.lines().filter(line -> line.startsWith("data:"))
                        .map(line -> line.substring(5).stripLeading())
                        .filter(line -> !line.isBlank())
                        .reduce((first, second) -> second)
                        .orElseThrow(() -> new McpProtocolException(
                                -32700, "MCP SSE response contains no data"));
            }
            return mapper.readTree(json);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new McpProtocolException(-32700, "Invalid MCP JSON response");
        }
    }

    private JsonNode result(JsonNode response, String expectedId) {
        if (response == null || !"2.0".equals(response.path("jsonrpc").asText())
                || !expectedId.equals(response.path("id").asText())) {
            throw new McpProtocolException(-32603, "Invalid MCP JSON-RPC response");
        }
        if (response.has("error")) {
            throw new McpProtocolException(response.path("error").path("code").asInt(-32603),
                    "MCP server rejected the request");
        }
        if (!response.path("result").isObject()) {
            throw new McpProtocolException(-32603, "MCP response has no result");
        }
        return response.path("result");
    }

    private ObjectNode request(String id, String method) {
        ObjectNode value = mapper.createObjectNode();
        value.put("jsonrpc", "2.0");
        value.put("id", id);
        value.put("method", method);
        return value;
    }

    private void close(String endpoint, String token, String sessionId) {
        if (sessionId == null) return;
        try {
            client.delete().uri(endpoint)
                    .header(McpProtocol.AUTHORIZATION_HEADER, token)
                    .header(McpProtocol.SESSION_HEADER, sessionId)
                    .exchange((request, response) -> null);
        } catch (RuntimeException ignored) {
            // Session cleanup is best effort; authorization expiry remains the hard boundary.
        }
    }

    record TransportResponse(int status, HttpHeaders headers, JsonNode body) {}
}
