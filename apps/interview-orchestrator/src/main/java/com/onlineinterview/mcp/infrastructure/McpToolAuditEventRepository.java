package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.McpToolAuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpToolAuditEventRepository extends JpaRepository<McpToolAuditEvent, UUID> {
    List<McpToolAuditEvent> findByActorSubjectOrderByOccurredAtDesc(String actorSubject);
    List<McpToolAuditEvent> findByResourceTypeAndResourceIdOrderByOccurredAtDesc(
            String resourceType, UUID resourceId);
}
