package com.onlineinterview.mcp.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Component;

@Component
public class McpSchemaValidator {
    static final int MAX_SCHEMA_BYTES = 16_384;
    private final ObjectMapper mapper;

    public McpSchemaValidator(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode validate(String schema) {
        if (schema == null || schema.getBytes(StandardCharsets.UTF_8).length > MAX_SCHEMA_BYTES) {
            throw new IllegalArgumentException("MCP schema is missing or exceeds 16 KiB");
        }
        try {
            JsonNode root = mapper.readTree(schema);
            if (!root.isObject()
                    || !"object".equals(root.path("type").asText())
                    || !root.path("properties").isObject()
                    || !root.has("additionalProperties")
                    || root.path("additionalProperties").asBoolean(true)) {
                throw new IllegalArgumentException(
                        "MCP schema must be a strict object schema");
            }
            rejectUnsafeKeywords(root);
            return root;
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("MCP schema is not valid JSON", exception);
        }
    }

    private void rejectUnsafeKeywords(JsonNode node) {
        if (node.isObject()) {
            if (node.has("$ref") || node.has("$dynamicRef")) {
                throw new IllegalArgumentException("Remote schema references are not allowed");
            }
            node.elements().forEachRemaining(this::rejectUnsafeKeywords);
        } else if (node.isArray()) {
            node.elements().forEachRemaining(this::rejectUnsafeKeywords);
        }
    }
}
