package com.onlineinterview.mcp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.onlineinterview.mcp.application.McpAuthorizationTokenService;
import com.onlineinterview.mcp.application.McpApprovalService;
import com.onlineinterview.mcp.application.McpRegistryService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/mcp/{serverKey}")
public class McpHostController {
    private final ObjectMapper mapper;
    private final McpAuthorizationTokenService tokens;
    private final McpSessionRegistry sessions;
    private final McpRegistryService registry;
    private final McpPayloadValidator payloads;
    private final McpControlledExecutionService execution;

    public McpHostController(ObjectMapper mapper, McpAuthorizationTokenService tokens,
            McpSessionRegistry sessions, McpRegistryService registry,
            McpPayloadValidator payloads, McpControlledExecutionService execution) {
        this.mapper = mapper;
        this.tokens = tokens;
        this.sessions = sessions;
        this.registry = registry;
        this.payloads = payloads;
        this.execution = execution;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<JsonNode> post(@PathVariable String serverKey,
            @RequestHeader(McpProtocol.AUTHORIZATION_HEADER) String authorization,
            @RequestHeader(value = McpProtocol.SESSION_HEADER, required = false) String sessionId,
            @RequestHeader(value = McpProtocol.VERSION_HEADER, required = false) String version,
            @RequestBody JsonNode request, HttpServletRequest httpRequest) {
        JsonNode id = request.get("id");
        try {
            rejectBrowserOrigin(httpRequest.getHeader(HttpHeaders.ORIGIN));
            requireJsonRpc(request);
            var context = tokens.verify(authorization);
            if (!context.serverKey().equals(serverKey)) {
                throw new McpProtocolException(-32003, "MCP server scope mismatch");
            }
            String method = request.path("method").asText();
            return switch (method) {
                case "initialize" -> initialize(id, request.path("params"), context);
                case "notifications/initialized" -> initialized(sessionId, context);
                case "tools/list" -> listTools(id, sessionId, version, context);
                case "tools/call" -> callTool(id, sessionId, version, context,
                        request.path("params"), httpRequest.getHeader("Idempotency-Key"));
                default -> throw new McpProtocolException(-32601, "Unsupported MCP method");
            };
        } catch (McpProtocolException exception) {
            if (httpRequest.getHeader(HttpHeaders.ORIGIN) != null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(error(id, exception.getCode(), exception.getMessage()));
            }
            return ResponseEntity.ok(error(id, exception.getCode(), exception.getMessage()));
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error(id, -32000, "Invalid MCP authorization"));
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> close(
            @RequestHeader(McpProtocol.AUTHORIZATION_HEADER) String authorization,
            @RequestHeader(McpProtocol.SESSION_HEADER) String sessionId) {
        var context = tokens.verify(authorization);
        sessions.remove(sessionId, context);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<JsonNode> initialize(JsonNode id, JsonNode params,
            com.onlineinterview.mcp.application.McpAuthorizationContext context) {
        if (!McpProtocol.VERSION.equals(params.path("protocolVersion").asText())) {
            throw new McpProtocolException(-32602, "Unsupported MCP protocol version");
        }
        String sessionId = sessions.create(context);
        ObjectNode result = mapper.createObjectNode();
        result.put("protocolVersion", McpProtocol.VERSION);
        result.set("capabilities", mapper.valueToTree(Map.of("tools", Map.of("listChanged", false))));
        result.set("serverInfo", mapper.valueToTree(
                Map.of("name", "online-interview-" + context.serverKey(), "version", "0.1.0")));
        return ResponseEntity.ok().header(McpProtocol.SESSION_HEADER, sessionId)
                .body(success(id, result));
    }

    private ResponseEntity<JsonNode> initialized(String sessionId,
            com.onlineinterview.mcp.application.McpAuthorizationContext context) {
        sessions.initialized(sessionId, context);
        return ResponseEntity.accepted().build();
    }

    private ResponseEntity<JsonNode> listTools(JsonNode id, String sessionId, String version,
            com.onlineinterview.mcp.application.McpAuthorizationContext context) {
        requireReady(sessionId, version, context);
        var tool = registry.resolve(context.serverKey(), context.toolName());
        var advertised = mapper.createObjectNode();
        advertised.put("name", tool.name());
        advertised.put("description", tool.description());
        advertised.set("inputSchema", tool.inputSchema());
        advertised.set("outputSchema", tool.outputSchema());
        ObjectNode result = mapper.createObjectNode();
        result.set("tools", mapper.valueToTree(java.util.List.of(advertised)));
        return ResponseEntity.ok(success(id, result));
    }

    private ResponseEntity<JsonNode> callTool(JsonNode id, String sessionId, String version,
            com.onlineinterview.mcp.application.McpAuthorizationContext context, JsonNode params,
            String idempotencyKey) {
        requireReady(sessionId, version, context);
        if (!context.toolName().equals(params.path("name").asText())) {
            throw new McpProtocolException(-32602, "MCP tool scope mismatch");
        }
        var tool = registry.resolve(context.serverKey(), context.toolName());
        JsonNode arguments = params.path("arguments");
        payloads.validate(arguments, tool.inputSchema());
        JsonNode output;
        try {
            output = execution.execute(context, tool.accessType(), arguments, idempotencyKey);
        } catch (McpApprovalService.McpProtocolApprovalException exception) {
            throw new McpProtocolException(-32004, exception.getMessage());
        }
        payloads.validate(output, tool.outputSchema());
        ObjectNode result = mapper.createObjectNode();
        result.set("content", mapper.valueToTree(java.util.List.of(
                Map.of("type", "text", "text", output.toString()))));
        result.set("structuredContent", output);
        result.put("isError", false);
        return ResponseEntity.ok(success(id, result));
    }

    private void requireReady(String sessionId, String version,
            com.onlineinterview.mcp.application.McpAuthorizationContext context) {
        if (!McpProtocol.VERSION.equals(version)) {
            throw new McpProtocolException(-32602, "Missing or unsupported MCP protocol version");
        }
        sessions.requireInitialized(sessionId, context);
    }

    private void requireJsonRpc(JsonNode request) {
        if (!request.isObject() || !"2.0".equals(request.path("jsonrpc").asText())
                || !request.path("method").isTextual()) {
            throw new McpProtocolException(-32600, "Invalid JSON-RPC request");
        }
    }

    private void rejectBrowserOrigin(String origin) {
        if (origin != null) throw new McpProtocolException(-32003, "Browser origins are forbidden");
    }

    private ObjectNode success(JsonNode id, JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id);
        response.set("result", result);
        return response;
    }

    private ObjectNode error(JsonNode id, int code, String message) {
        ObjectNode response = mapper.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", id == null ? mapper.nullNode() : id);
        response.set("error", mapper.valueToTree(Map.of("code", code, "message", message)));
        return response;
    }
}
