package com.onlineinterview.mcp.api;

import com.onlineinterview.mcp.application.McpResourceAuthorizationService;
import com.onlineinterview.mcp.infrastructure.McpToolAuditEventRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/mcp/audit")
@PreAuthorize("hasRole('INTERVIEWER')")
public class McpAuditController {
    private final McpToolAuditEventRepository audit;
    private final McpResourceAuthorizationService resources;

    public McpAuditController(
            McpToolAuditEventRepository audit, McpResourceAuthorizationService resources) {
        this.audit = audit;
        this.resources = resources;
    }

    @GetMapping
    public List<McpAuditResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam String resourceType, @RequestParam UUID resourceId) {
        if (!resources.isOwnedBy(resourceType, resourceId, jwt.getSubject())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        return audit.findByResourceTypeAndResourceIdOrderByOccurredAtDesc(resourceType, resourceId)
                .stream().map(McpAuditResponse::from).toList();
    }
}
