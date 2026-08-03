package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolAuditEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class McpExecutionObserverTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private final McpToolAuditEventRepository audit = mock(McpToolAuditEventRepository.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final McpExecutionObserver observer = new McpExecutionObserver(
            new McpResultScanner(), audit, meters, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void appendsLifecycleAuditAndMetrics() {
        var context = context();
        var executionId = UUID.randomUUID();
        var result = new ObjectMapper().createObjectNode().put("accepted", true);

        observer.started(executionId, context);
        assertThat(observer.succeeded(executionId, context, result, NOW)).isSameAs(result);
        observer.failed(executionId, context, "timeout", NOW);

        var events = ArgumentCaptor.forClass(McpToolAuditEvent.class);
        verify(audit, times(3)).save(events.capture());
        assertThat(events.getAllValues()).extracting(McpToolAuditEvent::getEventType)
                .containsExactly(McpAuditEventType.STARTED, McpAuditEventType.SUCCEEDED,
                        McpAuditEventType.FAILED);
        assertThat(meters.get("mcp.tool.calls").counter().count()).isEqualTo(1);
        assertThat(meters.get("mcp.tool.failures").counter().count()).isEqualTo(1);
        assertThat(meters.get("mcp.tool.duration").timers()).hasSize(2);
    }

    @Test
    void noopStillScansResults() {
        var unsafe = new ObjectMapper().createObjectNode().put("password", "x");
        assertThatThrownBy(() -> McpExecutionObserver.noop().succeeded(
                UUID.randomUUID(), context(), unsafe, NOW))
                .isInstanceOf(com.onlineinterview.mcp.transport.McpProtocolException.class);
    }

    private static McpAuthorizationContext context() {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.ANSWER_EVALUATION,
                "result", "submit_ai_evaluation", "service", McpActorRole.SERVICE,
                "SESSION", UUID.randomUUID(), 1, true, NOW, NOW.plusSeconds(60));
    }
}
