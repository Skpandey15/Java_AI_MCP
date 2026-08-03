package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class McpPayloadValidator {
    public void validate(JsonNode value, JsonNode schema) {
        validateNode(value, schema, "$");
    }

    private void validateNode(JsonNode value, JsonNode schema, String path) {
        String type = schema.path("type").asText();
        boolean valid = switch (type) {
            case "object" -> value != null && value.isObject();
            case "array" -> value != null && value.isArray();
            case "string" -> value != null && value.isTextual();
            case "integer" -> value != null && value.isIntegralNumber();
            case "number" -> value != null && value.isNumber();
            case "boolean" -> value != null && value.isBoolean();
            default -> false;
        };
        if (!valid) throw new McpProtocolException(-32602, "Invalid MCP payload at " + path);
        if ("object".equals(type)) validateObject(value, schema, path);
        if ("array".equals(type) && schema.has("items")) {
            int index = 0;
            for (JsonNode item : value) validateNode(item, schema.path("items"), path + "[" + index++ + "]");
        }
    }

    private void validateObject(JsonNode value, JsonNode schema, String path) {
        Set<String> allowed = new HashSet<>();
        schema.path("properties").propertyNames().forEach(allowed::add);
        if (!schema.path("additionalProperties").asBoolean(true)) {
            value.propertyNames().forEach(name -> {
                if (!allowed.contains(name)) {
                    throw new McpProtocolException(-32602,
                            "Unexpected MCP property at " + path + "." + name);
                }
            });
        }
        for (JsonNode required : schema.path("required")) {
            if (!value.has(required.asText())) {
                throw new McpProtocolException(-32602,
                        "Missing MCP property at " + path + "." + required.asText());
            }
        }
        value.properties().forEach(entry -> {
            JsonNode propertySchema = schema.path("properties").path(entry.getKey());
            if (!propertySchema.isMissingNode()) {
                validateNode(entry.getValue(), propertySchema, path + "." + entry.getKey());
            }
        });
    }
}
