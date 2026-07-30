package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpToolApprovalRepository extends JpaRepository<McpToolApproval, UUID> {
    Optional<McpToolApproval> findByContextId(UUID contextId);
    List<McpToolApproval> findByStatusAndExpiresAtAfterOrderByRequestedAtAsc(
            McpApprovalStatus status, Instant now);
}
