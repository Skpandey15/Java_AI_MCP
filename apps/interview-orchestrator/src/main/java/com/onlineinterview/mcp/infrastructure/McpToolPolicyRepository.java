package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.*;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpToolPolicyRepository extends JpaRepository<McpToolPolicy, UUID> {
    Optional<McpToolPolicy> findByWorkflowAndTool_IdAndActorRoleAndEnabledTrue(
            McpWorkflow workflow, UUID toolId, McpActorRole actorRole);
}
