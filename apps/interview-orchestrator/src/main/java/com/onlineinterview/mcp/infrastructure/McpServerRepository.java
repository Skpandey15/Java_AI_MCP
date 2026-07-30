package com.onlineinterview.mcp.infrastructure;

import com.onlineinterview.mcp.domain.McpServer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServer, UUID> {
    List<McpServer> findByEnabledTrueOrderByKeyAsc();
    Optional<McpServer> findByKeyAndEnabledTrue(String key);
}
