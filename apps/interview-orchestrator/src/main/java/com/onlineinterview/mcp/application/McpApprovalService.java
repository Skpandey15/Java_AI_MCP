package com.onlineinterview.mcp.application;

import com.onlineinterview.mcp.domain.*;
import com.onlineinterview.mcp.infrastructure.McpToolApprovalRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class McpApprovalService {
    private final McpToolApprovalRepository approvals;
    private final McpResourceAuthorizationService resources;
    private final Clock clock;

    @Autowired
    public McpApprovalService(McpToolApprovalRepository approvals,
            McpResourceAuthorizationService resources) {
        this(approvals, resources, Clock.systemUTC());
    }

    McpApprovalService(McpToolApprovalRepository approvals,
            McpResourceAuthorizationService resources, Clock clock) {
        this.approvals = approvals;
        this.resources = resources;
        this.clock = clock;
    }

    @Transactional
    public McpToolApproval request(McpAuthorizationContext context) {
        if (!context.approvalRequired()) {
            throw new IllegalArgumentException("Authorization does not require approval");
        }
        return approvals.findByContextId(context.contextId())
                .orElseGet(() -> approvals.save(McpToolApproval.pending(context)));
    }

    @Transactional(readOnly = true)
    public List<McpToolApproval> pending(String approver) {
        return approvals.findByStatusAndExpiresAtAfterOrderByRequestedAtAsc(
                        McpApprovalStatus.PENDING, clock.instant()).stream()
                .filter(value -> resources.isOwnedBy(
                        value.getResourceType(), value.getResourceId(), approver))
                .toList();
    }

    @Transactional
    public McpToolApproval decide(UUID approvalId, String approver, boolean approved) {
        var value = approvals.findById(approvalId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "MCP approval not found"));
        if (!resources.isOwnedBy(value.getResourceType(), value.getResourceId(), approver)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "MCP approval not found");
        }
        value.decide(approved, approver, clock.instant());
        return value;
    }

    @Transactional(readOnly = true)
    public void requireApproved(McpAuthorizationContext context) {
        if (!context.approvalRequired()) return;
        var approval = approvals.findByContextId(context.contextId())
                .filter(value -> value.getStatus() == McpApprovalStatus.APPROVED)
                .filter(value -> value.getExpiresAt().isAfter(clock.instant()))
                .orElseThrow(() -> new McpProtocolApprovalException(
                        "MCP tool approval is required"));
        if (!approval.getResourceId().equals(context.resourceId())
                || !approval.getToolName().equals(context.toolName())) {
            throw new McpProtocolApprovalException("MCP approval scope mismatch");
        }
    }

    public static class McpProtocolApprovalException extends RuntimeException {
        public McpProtocolApprovalException(String message) { super(message); }
    }
}
