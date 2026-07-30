package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class McpSchemaValidatorTest {
    private final McpSchemaValidator validator = new McpSchemaValidator(new ObjectMapper());

    @Test
    void acceptsStrictLocalObjectSchema() {
        var schema = validator.validate("""
                {"type":"object","properties":{"id":{"type":"string"}},
                 "required":["id"],"additionalProperties":false}
                """);

        assertThat(schema.path("properties").has("id")).isTrue();
    }

    @Test
    void rejectsMalformedLooseReferencedAndOversizedSchemas() {
        assertThatThrownBy(() -> validator.validate("not-json"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(
                "{\"type\":\"object\",\"properties\":{}}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("""
                {"type":"object","properties":{"value":{"$ref":"https://evil/schema"}},
                 "additionalProperties":false}
                """)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.validate("x".repeat(16_385)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
