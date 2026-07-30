package com.onlineinterview.mcp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class McpDomainTest {
    @Test
    void exposesPersistedPolicyAndRegistryMetadata() {
        var server = new McpServer();
        var tool = new McpTool();
        var policy = new McpToolPolicy();
        var now = Instant.parse("2026-07-30T10:00:00Z");
        var policyId = UUID.randomUUID();

        ReflectionTestUtils.setField(server, "baseUrl", "http://internal");
        ReflectionTestUtils.setField(server, "enabled", true);
        ReflectionTestUtils.setField(server, "createdAt", now);
        ReflectionTestUtils.setField(tool, "server", server);
        ReflectionTestUtils.setField(tool, "createdAt", now);
        ReflectionTestUtils.setField(policy, "id", policyId);
        ReflectionTestUtils.setField(policy, "workflow", McpWorkflow.ANSWER_EVALUATION);
        ReflectionTestUtils.setField(policy, "tool", tool);
        ReflectionTestUtils.setField(policy, "actorRole", McpActorRole.SERVICE);
        ReflectionTestUtils.setField(policy, "approvalRequired", true);
        ReflectionTestUtils.setField(policy, "maxCalls", 1);
        ReflectionTestUtils.setField(policy, "ttlSeconds", 60);
        ReflectionTestUtils.setField(policy, "enabled", true);
        ReflectionTestUtils.setField(policy, "createdAt", now);

        assertThat(server.getBaseUrl()).isEqualTo("http://internal");
        assertThat(server.isEnabled()).isTrue();
        assertThat(server.getCreatedAt()).isEqualTo(now);
        assertThat(tool.getServer()).isSameAs(server);
        assertThat(tool.getCreatedAt()).isEqualTo(now);
        assertThat(policy.getId()).isEqualTo(policyId);
        assertThat(policy.getWorkflow()).isEqualTo(McpWorkflow.ANSWER_EVALUATION);
        assertThat(policy.getTool()).isSameAs(tool);
        assertThat(policy.getActorRole()).isEqualTo(McpActorRole.SERVICE);
        assertThat(policy.isApprovalRequired()).isTrue();
        assertThat(policy.getMaxCalls()).isEqualTo(1);
        assertThat(policy.getTtlSeconds()).isEqualTo(60);
        assertThat(policy.isEnabled()).isTrue();
        assertThat(policy.getCreatedAt()).isEqualTo(now);
    }
}
