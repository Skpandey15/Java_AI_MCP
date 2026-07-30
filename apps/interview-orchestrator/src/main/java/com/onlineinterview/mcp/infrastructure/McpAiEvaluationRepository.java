package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.McpAiEvaluation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpAiEvaluationRepository extends JpaRepository<McpAiEvaluation, UUID> {
}
