package com.onlineinterview.mcp.application;

import tools.jackson.databind.JsonNode;
import com.onlineinterview.mcp.transport.McpProtocolException;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class McpResultScanner {
    private static final Pattern SENSITIVE_NAME = Pattern.compile(
            "(?i).*(password|secret|token|authorization|api.?key|private.?key).*");
    private static final Pattern SENSITIVE_VALUE = Pattern.compile(
            "(?i).*(bearer\\s+[a-z0-9._-]{12,}|-----BEGIN [A-Z ]*PRIVATE KEY-----).*");

    public JsonNode requireSafe(JsonNode result) {
        if (result == null || result.isNull()) {
            throw new McpProtocolException(-32012, "MCP tool returned an empty result");
        }
        scan(result);
        return result;
    }

    private void scan(JsonNode node) {
        if (node.isObject()) {
            for (var field : node.properties()) {
                if (SENSITIVE_NAME.matcher(field.getKey()).matches()) reject();
                scan(field.getValue());
            }
        } else if (node.isArray()) {
            node.forEach(this::scan);
        } else if (node.isTextual() && SENSITIVE_VALUE.matcher(node.textValue()).matches()) {
            reject();
        }
    }

    private void reject() {
        throw new McpProtocolException(-32012, "MCP tool result failed sensitive-data scanning");
    }
}
