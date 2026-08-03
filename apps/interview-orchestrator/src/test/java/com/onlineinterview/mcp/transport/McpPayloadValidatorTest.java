package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class McpPayloadValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpPayloadValidator validator = new McpPayloadValidator();

    @Test
    void validatesNestedStrictPayload() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{
                  "name":{"type":"string"},
                  "count":{"type":"integer"},
                  "score":{"type":"number"},
                  "active":{"type":"boolean"},
                  "tags":{"type":"array","items":{"type":"string"}},
                  "meta":{"type":"object","properties":{},"additionalProperties":false}
                },"required":["name"],"additionalProperties":false}
                """);
        validator.validate(mapper.readTree("""
                {"name":"Java","count":2,"score":0.9,"active":true,"tags":["jvm"],"meta":{}}
                """), schema);
    }

    @Test
    void rejectsWrongMissingAndAdditionalValues() throws Exception {
        var schema = mapper.readTree("""
                {"type":"object","properties":{"name":{"type":"string"}},
                 "required":["name"],"additionalProperties":false}
                """);
        assertThatThrownBy(() -> validator.validate(mapper.readTree("{}"), schema))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> validator.validate(
                mapper.readTree("{\"name\":1}"), schema))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> validator.validate(
                mapper.readTree("{\"name\":\"x\",\"extra\":true}"), schema))
                .isInstanceOf(McpProtocolException.class);
        assertThatThrownBy(() -> validator.validate(
                mapper.readTree("\"x\""), mapper.readTree("{\"type\":\"unsupported\"}")))
                .isInstanceOf(McpProtocolException.class);
    }
}
