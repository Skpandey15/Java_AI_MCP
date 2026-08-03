package com.onlineinterview.mcp.transport;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.application.*;
import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.*;
import java.time.*;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class McpControlledExecutionServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private final McpApprovalService approvals = mock(McpApprovalService.class);
    private final McpContextUsageStore usage = mock(McpContextUsageStore.class);
    private final McpToolExecutionRepository executions = mock(McpToolExecutionRepository.class);
    private final McpToolDispatcher dispatcher = mock(McpToolDispatcher.class);
    private final ExecutorService executor = mock(ExecutorService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final McpExecutionProperties properties = new McpExecutionProperties();
    private final McpControlledExecutionService service = new McpControlledExecutionService(
            approvals, usage, executions, dispatcher, properties, mapper, executor,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @SuppressWarnings("unchecked")
    void executesApprovedQuotaControlledCallAndPersistsResult() throws Exception {
        var context = context();
        var arguments = mapper.createObjectNode().put("value", 1);
        var output = mapper.createObjectNode().put("accepted", true);
        when(usage.consume(context.contextId(), 1, NOW)).thenReturn(true);
        when(executor.submit(any(Callable.class)))
                .thenReturn(CompletableFuture.completedFuture(output));

        var result = service.execute(
                context, McpAccessType.STATE_CHANGING, arguments, "key-1");

        assertThat(result).isEqualTo(output);
        verify(approvals).requireApproved(context);
        var record = org.mockito.ArgumentCaptor.forClass(McpToolExecution.class);
        verify(executions).saveAndFlush(record.capture());
        assertThat(record.getValue().getStatus()).isEqualTo(McpExecutionStatus.SUCCEEDED);
        verify(executions).save(record.getValue());
    }

    @Test
    void replaysSuccessfulIdempotentResultWithoutConsumingQuota() {
        var context = context();
        var record = McpToolExecution.started(context.contextId(), "key", NOW);
        record.succeed("{\"accepted\":true}", NOW);
        when(executions.findByContextIdAndIdempotencyKey(context.contextId(), "key"))
                .thenReturn(Optional.of(record));

        assertThat(service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), "key").path("accepted").asBoolean()).isTrue();
        verifyNoInteractions(usage, dispatcher);
    }

    @Test
    @SuppressWarnings("unchecked")
    void enforcesIdempotencyQuotaTimeoutAndPreviousState() throws Exception {
        var context = context();
        assertThatThrownBy(() -> service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), null)).isInstanceOf(McpProtocolException.class);

        when(usage.consume(context.contextId(), 1, NOW)).thenReturn(false);
        assertThatThrownBy(() -> service.execute(context, McpAccessType.READ_ONLY,
                mapper.createObjectNode(), null)).isInstanceOf(McpProtocolException.class);

        when(usage.consume(context.contextId(), 1, NOW)).thenReturn(true);
        Future<JsonNode> timeout = mock(Future.class);
        when(executor.submit(any(Callable.class))).thenReturn(timeout);
        when(timeout.get(anyLong(), any())).thenThrow(new TimeoutException());
        assertThatThrownBy(() -> service.execute(context, McpAccessType.READ_ONLY,
                mapper.createObjectNode(), null)).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("timed out");
        verify(timeout).cancel(true);

        var running = McpToolExecution.started(context.contextId(), "running", NOW);
        when(executions.findByContextIdAndIdempotencyKey(
                context.contextId(), "running")).thenReturn(Optional.of(running));
        assertThatThrownBy(() -> service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), "running")).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("in progress");

        var failed = McpToolExecution.started(context.contextId(), "failed", NOW);
        failed.fail(NOW);
        when(executions.findByContextIdAndIdempotencyKey(
                context.contextId(), "failed")).thenReturn(Optional.of(failed));
        assertThatThrownBy(() -> service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), "failed")).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void closesExecutor() {
        service.close();
        verify(executor).close();
    }

    @Test
    void exposesConfiguredExecutionLimits() {
        properties.setTimeoutSeconds(7);
        properties.setCallsPerMinute(11);
        assertThat(properties.getTimeoutSeconds()).isEqualTo(7);
        assertThat(properties.getCallsPerMinute()).isEqualTo(11);
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordsInterruptedAndFailedExecutions() throws Exception {
        var context = context();
        when(usage.consume(context.contextId(), 1, NOW)).thenReturn(true);
        Future<JsonNode> interrupted = mock(Future.class);
        when(executor.submit(any(Callable.class))).thenReturn(interrupted);
        when(interrupted.get(anyLong(), any())).thenThrow(new InterruptedException());

        assertThatThrownBy(() -> service.execute(context, McpAccessType.READ_ONLY,
                mapper.createObjectNode(), null)).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.interrupted()).isTrue();

        reset(executor);
        Future<JsonNode> failed = mock(Future.class);
        when(executor.submit(any(Callable.class))).thenReturn(failed);
        when(failed.get(anyLong(), any())).thenThrow(
                new ExecutionException(new IllegalStateException("broken")));
        assertThatThrownBy(() -> service.execute(context, McpAccessType.READ_ONLY,
                mapper.createObjectNode(), null)).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void replaysConcurrentInsertAndRejectsCorruptStoredResult() {
        var context = context();
        var replay = McpToolExecution.started(context.contextId(), "duplicate", NOW);
        replay.succeed("{\"cached\":true}", NOW);
        when(usage.consume(context.contextId(), 1, NOW)).thenReturn(true);
        when(executions.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("race"));
        when(executions.findByContextIdAndIdempotencyKey(context.contextId(), "duplicate"))
                .thenReturn(Optional.empty(), Optional.of(replay));

        assertThat(service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), "duplicate").path("cached").asBoolean()).isTrue();

        var corrupt = McpToolExecution.started(context.contextId(), "corrupt", NOW);
        corrupt.succeed("{", NOW);
        when(executions.findByContextIdAndIdempotencyKey(context.contextId(), "corrupt"))
                .thenReturn(Optional.of(corrupt));
        assertThatThrownBy(() -> service.execute(context, McpAccessType.STATE_CHANGING,
                mapper.createObjectNode(), "corrupt")).isInstanceOf(McpProtocolException.class)
                .hasMessageContaining("invalid");
    }

    private static McpAuthorizationContext context() {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.ANSWER_EVALUATION,
                "result", "submit_ai_evaluation", "service", McpActorRole.SERVICE,
                "SESSION", UUID.randomUUID(), 1, true, NOW, NOW.plusSeconds(60));
    }
}
