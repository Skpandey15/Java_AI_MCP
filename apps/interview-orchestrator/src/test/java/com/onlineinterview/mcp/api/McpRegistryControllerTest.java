package com.onlineinterview.mcp.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.onlineinterview.mcp.application.McpRegistryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "app.ai-service.base-url=http://localhost",
        "app.ai-service.service-token=test-token"
})
@AutoConfigureMockMvc
class McpRegistryControllerTest {
    @Autowired MockMvc mvc;
    @Autowired McpRegistryService registry;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedRegistry() {
        jdbc.update("DELETE FROM mcp_tool");
        jdbc.update("DELETE FROM mcp_server");
        insertServer("10000000-0000-0000-0000-000000000001", "interview");
        insertServer("10000000-0000-0000-0000-000000000004", "result");
        insertTool("20000000-0000-0000-0000-000000000001",
                "10000000-0000-0000-0000-000000000001",
                "get_interview_context", "READ_ONLY");
        insertTool("20000000-0000-0000-0000-000000000004",
                "10000000-0000-0000-0000-000000000004",
                "submit_ai_evaluation", "STATE_CHANGING");
    }

    @Test
    void listsSeededEnabledToolsWithoutExposingEndpoint() throws Exception {
        mvc.perform(get("/api/v1/mcp/registry").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_INTERVIEWER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("interview"))
                .andExpect(jsonPath("$[0].tools[0].name").value("get_interview_context"))
                .andExpect(jsonPath("$[0].tools[0].inputSchema.additionalProperties").value(false))
                .andExpect(jsonPath("$[0].tools[0].accessType").value("READ_ONLY"))
                .andExpect(jsonPath("$[0].baseUrl").doesNotExist());

        var tool = registry.resolve("result", "submit_ai_evaluation");
        assertThat(tool.baseUrl()).endsWith("/internal/mcp/result");
        assertThat(tool.accessType().name()).isEqualTo("STATE_CHANGING");
    }

    @Test
    void deniesCandidatesAndRejectsUnknownTools() throws Exception {
        mvc.perform(get("/api/v1/mcp/registry").with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CANDIDATE"))))
                .andExpect(status().isForbidden());
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> registry.resolve("missing", "tool")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        assertThat(org.assertj.core.api.Assertions.catchThrowable(
                () -> registry.resolve("interview", "missing")))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private void insertServer(String id, String key) {
        jdbc.update("""
                INSERT INTO mcp_server
                (id, server_key, display_name, base_url, transport, classification, enabled, created_at)
                VALUES (?, ?, ?, ?, 'STREAMABLE_HTTP', 'INTERNAL', TRUE, CURRENT_TIMESTAMP)
                """, java.util.UUID.fromString(id), key, key + " MCP",
                "http://service/internal/mcp/" + key);
    }

    private void insertTool(String id, String serverId, String name, String accessType) {
        String schema = """
                {"type":"object","properties":{"id":{"type":"string"}},
                 "required":["id"],"additionalProperties":false}
                """;
        jdbc.update("""
                INSERT INTO mcp_tool
                (id, server_id, tool_name, description, input_schema, output_schema,
                 access_type, candidate_safe, enabled, created_at)
                VALUES (?, ?, ?, 'Approved tool', ?, ?, ?, FALSE, TRUE, CURRENT_TIMESTAMP)
                """, java.util.UUID.fromString(id), java.util.UUID.fromString(serverId),
                name, schema, schema, accessType);
    }
}
