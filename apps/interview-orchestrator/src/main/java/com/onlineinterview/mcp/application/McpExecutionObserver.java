package com.onlineinterview.mcp.application;

import tools.jackson.databind.JsonNode;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolAuditEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class McpExecutionObserver {
    private final McpResultScanner scanner;
    private final McpToolAuditEventRepository audit;
    private final MeterRegistry meters;
    private final Clock clock;

    @Autowired
    public McpExecutionObserver(
            McpResultScanner scanner, McpToolAuditEventRepository audit, MeterRegistry meters) {
        this(scanner, audit, meters, Clock.systemUTC());
    }

    McpExecutionObserver(McpResultScanner scanner, McpToolAuditEventRepository audit,
            MeterRegistry meters, Clock clock) {
        this.scanner = scanner;
        this.audit = audit;
        this.meters = meters;
        this.clock = clock;
    }

    public void started(UUID executionId, McpAuthorizationContext context) {
        if (audit != null) {
            audit.save(McpToolAuditEvent.create(
                    executionId, context, McpAuditEventType.STARTED, null, clock.instant()));
            meters.counter("mcp.tool.calls", "server", context.serverKey(),
                    "tool", context.toolName()).increment();
        }
    }

    public JsonNode succeeded(
            UUID executionId, McpAuthorizationContext context, JsonNode result, Instant started) {
        var safe = scanner.requireSafe(result);
        if (audit != null) {
            audit.save(McpToolAuditEvent.create(
                    executionId, context, McpAuditEventType.SUCCEEDED, null, clock.instant()));
            meters.timer("mcp.tool.duration", "server", context.serverKey(),
                    "tool", context.toolName(), "outcome", "success")
                    .record(Duration.between(started, clock.instant()));
        }
        return safe;
    }

    public void failed(UUID executionId, McpAuthorizationContext context,
            String reason, Instant started) {
        if (audit != null) {
            audit.save(McpToolAuditEvent.create(
                    executionId, context, McpAuditEventType.FAILED, reason, clock.instant()));
            meters.counter("mcp.tool.failures", "server", context.serverKey(),
                    "tool", context.toolName(), "reason", reason).increment();
            meters.timer("mcp.tool.duration", "server", context.serverKey(),
                    "tool", context.toolName(), "outcome", "failure")
                    .record(Duration.between(started, clock.instant()));
        }
    }

    public static McpExecutionObserver noop() {
        return new McpExecutionObserver(new McpResultScanner(), null, null, Clock.systemUTC());
    }
}
