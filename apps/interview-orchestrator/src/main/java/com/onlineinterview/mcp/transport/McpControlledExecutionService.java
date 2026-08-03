package com.onlineinterview.mcp.transport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.application.*;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.*;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class McpControlledExecutionService implements AutoCloseable {
    private final McpApprovalService approvals;
    private final McpContextUsageStore usage;
    private final McpToolExecutionRepository executions;
    private final McpToolDispatcher dispatcher;
    private final McpExecutionProperties properties;
    private final ObjectMapper mapper;
    private final McpExecutionObserver observer;
    private final ExecutorService executor;
    private final Clock clock;

    @Autowired
    public McpControlledExecutionService(McpApprovalService approvals,
            McpContextUsageStore usage, McpToolExecutionRepository executions,
            McpToolDispatcher dispatcher, McpExecutionProperties properties,
            ObjectMapper mapper, McpExecutionObserver observer) {
        this(approvals, usage, executions, dispatcher, properties, mapper, observer,
                Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC());
    }

    McpControlledExecutionService(McpApprovalService approvals,
            McpContextUsageStore usage, McpToolExecutionRepository executions,
            McpToolDispatcher dispatcher, McpExecutionProperties properties,
            ObjectMapper mapper, ExecutorService executor, Clock clock) {
        this(approvals, usage, executions, dispatcher, properties, mapper,
                McpExecutionObserver.noop(), executor, clock);
    }

    McpControlledExecutionService(McpApprovalService approvals,
            McpContextUsageStore usage, McpToolExecutionRepository executions,
            McpToolDispatcher dispatcher, McpExecutionProperties properties,
            ObjectMapper mapper, McpExecutionObserver observer,
            ExecutorService executor, Clock clock) {
        this.approvals = approvals;
        this.usage = usage;
        this.executions = executions;
        this.dispatcher = dispatcher;
        this.properties = properties;
        this.mapper = mapper;
        this.observer = observer;
        this.executor = executor;
        this.clock = clock;
    }

    public JsonNode execute(McpAuthorizationContext context, McpAccessType accessType,
            JsonNode arguments, String idempotencyKey) {
        String key = normalizeKey(accessType, idempotencyKey);
        if (key != null) {
            var existing = executions.findByContextIdAndIdempotencyKey(context.contextId(), key);
            if (existing.isPresent()) return replay(existing.get());
        }
        approvals.requireApproved(context);
        int limit = Math.min(context.maxCalls(), properties.getCallsPerMinute());
        if (!usage.consume(context.contextId(), limit, clock.instant())) {
            throw new McpProtocolException(-32008, "MCP call limit exceeded");
        }
        var record = McpToolExecution.started(context.contextId(), key, clock.instant());
        try {
            executions.saveAndFlush(record);
        } catch (DataIntegrityViolationException conflict) {
            return executions.findByContextIdAndIdempotencyKey(context.contextId(), key)
                    .map(this::replay)
                    .orElseThrow(() -> conflict);
        }
        observer.started(record.getId(), context);
        Future<JsonNode> future = executor.submit(() -> dispatcher.execute(context, arguments));
        try {
            JsonNode result = future.get(properties.getTimeoutSeconds(), TimeUnit.SECONDS);
            result = observer.succeeded(record.getId(), context, result, record.getStartedAt());
            record.succeed(result.toString(), clock.instant());
            executions.save(record);
            return result;
        } catch (TimeoutException exception) {
            future.cancel(true);
            record.fail(clock.instant());
            executions.save(record);
            observer.failed(record.getId(), context, "timeout", record.getStartedAt());
            throw new McpProtocolException(-32009, "MCP tool execution timed out");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            record.fail(clock.instant());
            executions.save(record);
            observer.failed(record.getId(), context, "interrupted", record.getStartedAt());
            throw new McpProtocolException(-32009, "MCP tool execution interrupted");
        } catch (ExecutionException exception) {
            record.fail(clock.instant());
            executions.save(record);
            observer.failed(record.getId(), context, "execution", record.getStartedAt());
            if (exception.getCause() instanceof McpProtocolException protocol) throw protocol;
            throw new McpProtocolException(-32603, "MCP tool execution failed");
        } catch (McpProtocolException exception) {
            record.fail(clock.instant());
            executions.save(record);
            observer.failed(record.getId(), context, "unsafe_result", record.getStartedAt());
            throw exception;
        }
    }

    private String normalizeKey(McpAccessType accessType, String key) {
        if (accessType == McpAccessType.STATE_CHANGING
                && (key == null || key.isBlank() || key.length() > 200)) {
            throw new McpProtocolException(-32602,
                    "State-changing MCP tools require a valid idempotency key");
        }
        return key == null || key.isBlank() ? null : key;
    }

    private JsonNode replay(McpToolExecution execution) {
        if (execution.getStatus() == McpExecutionStatus.IN_PROGRESS) {
            throw new McpProtocolException(-32010, "MCP tool execution is already in progress");
        }
        if (execution.getStatus() != McpExecutionStatus.SUCCEEDED) {
            throw new McpProtocolException(-32011, "Previous MCP tool execution failed");
        }
        try {
            return mapper.readTree(execution.getResultJson());
        } catch (tools.jackson.core.JacksonException exception) {
            throw new McpProtocolException(-32603, "Stored MCP result is invalid");
        }
    }

    @Override
    public void close() { executor.close(); }
}
