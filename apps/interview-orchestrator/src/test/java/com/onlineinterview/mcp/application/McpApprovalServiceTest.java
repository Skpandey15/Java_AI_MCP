package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolApprovalRepository;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class McpApprovalServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");
    private final McpToolApprovalRepository repository = mock(McpToolApprovalRepository.class);
    private final McpResourceAuthorizationService resources =
            mock(McpResourceAuthorizationService.class);
    private final McpApprovalService service = new McpApprovalService(repository, resources,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void createsOnceFiltersByOwnershipAndApproves() {
        var context = context(true);
        when(repository.findByContextId(context.contextId())).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var approval = service.request(context);
        when(repository.findByStatusAndExpiresAtAfterOrderByRequestedAtAsc(
                McpApprovalStatus.PENDING, NOW)).thenReturn(List.of(approval));
        when(resources.isOwnedBy("SESSION", approval.getResourceId(), "owner"))
                .thenReturn(true);
        when(repository.findById(approval.getId())).thenReturn(Optional.of(approval));

        assertThat(service.pending("owner")).containsExactly(approval);
        assertThat(service.decide(approval.getId(), "owner", true).getStatus())
                .isEqualTo(McpApprovalStatus.APPROVED);
        when(repository.findByContextId(context.contextId())).thenReturn(Optional.of(approval));
        service.requireApproved(context);
    }

    @Test
    void rejectsUnneededMissingUnownedAndUnapprovedRequests() {
        assertThatThrownBy(() -> service.request(context(false)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(UUID.randomUUID(), "owner", true))
                .isInstanceOf(ResponseStatusException.class);
        var approval = McpToolApproval.pending(context(true));
        when(repository.findById(approval.getId())).thenReturn(Optional.of(approval));
        assertThatThrownBy(() -> service.decide(approval.getId(), "other", false))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> service.requireApproved(context(true)))
                .isInstanceOf(McpApprovalService.McpProtocolApprovalException.class);
        service.requireApproved(context(false));
    }

    private static McpAuthorizationContext context(boolean required) {
        return new McpAuthorizationContext(UUID.randomUUID(), McpWorkflow.ANSWER_EVALUATION,
                "result", "submit_ai_evaluation", "service", McpActorRole.SERVICE,
                "SESSION", UUID.randomUUID(), 1, required, NOW, NOW.plusSeconds(60));
    }
}
