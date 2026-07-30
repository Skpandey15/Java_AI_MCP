package com.onlineinterview.mcp.api;

import com.onlineinterview.mcp.application.McpApprovalService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/mcp/approvals")
@PreAuthorize("hasRole('INTERVIEWER')")
public class McpApprovalController {
    private final McpApprovalService approvals;

    public McpApprovalController(McpApprovalService approvals) { this.approvals = approvals; }

    @GetMapping
    public List<McpApprovalResponse> pending(@AuthenticationPrincipal Jwt jwt) {
        return approvals.pending(jwt.getSubject()).stream()
                .map(McpApprovalResponse::from).toList();
    }

    @PostMapping("/{approvalId}/decision")
    public McpApprovalResponse decide(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID approvalId,
            @Valid @RequestBody McpApprovalDecisionRequest request) {
        return McpApprovalResponse.from(approvals.decide(
                approvalId, jwt.getSubject(), request.approved()));
    }
}
