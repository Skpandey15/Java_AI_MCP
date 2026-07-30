package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.transport.McpProtocolException;
import org.junit.jupiter.api.Test;

class McpResultScannerTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpResultScanner scanner = new McpResultScanner();

    @Test
    void acceptsOrdinaryNestedStructuredResults() {
        var result = mapper.createObjectNode();
        result.putArray("citations").addObject().put("content", "safe");
        assertThat(scanner.requireSafe(result)).isSameAs(result);
    }

    @Test
    void rejectsEmptySensitiveNamesAndSensitiveValues() {
        assertThatThrownBy(() -> scanner.requireSafe(null))
                .isInstanceOf(McpProtocolException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> scanner.requireSafe(mapper.nullNode()))
                .isInstanceOf(McpProtocolException.class).hasMessageContaining("empty");
        assertThatThrownBy(() -> scanner.requireSafe(
                mapper.createObjectNode().put("apiKey", "anything")))
                .isInstanceOf(McpProtocolException.class).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> scanner.requireSafe(
                mapper.createArrayNode().add("Bearer abcdefghijklmnop")))
                .isInstanceOf(McpProtocolException.class).hasMessageContaining("sensitive");
        assertThatThrownBy(() -> scanner.requireSafe(
                mapper.createObjectNode().put("value", "-----BEGIN PRIVATE KEY-----")))
                .isInstanceOf(McpProtocolException.class).hasMessageContaining("sensitive");
    }
}
