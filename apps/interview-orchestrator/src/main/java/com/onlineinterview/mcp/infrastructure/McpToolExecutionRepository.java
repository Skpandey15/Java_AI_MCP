package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.McpToolExecution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpToolExecutionRepository extends JpaRepository<McpToolExecution, UUID> {
    Optional<McpToolExecution> findByContextIdAndIdempotencyKey(UUID contextId, String key);
}
