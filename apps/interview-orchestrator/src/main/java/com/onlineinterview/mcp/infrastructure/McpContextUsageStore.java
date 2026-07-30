package com.onlineinterview.mcp.infrastructure;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class McpContextUsageStore {
    private final NamedParameterJdbcTemplate jdbc;

    public McpContextUsageStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean consume(UUID contextId, int limit, Instant now) {
        var values = Map.<String, Object>of(
                "contextId", contextId,
                "now", now,
                "windowCutoff", now.minus(1, ChronoUnit.MINUTES),
                "limit", limit);
        return !jdbc.query("""
                INSERT INTO mcp_context_usage(context_id, window_started_at, call_count)
                VALUES (:contextId, :now, 1)
                ON CONFLICT (context_id) DO UPDATE SET
                    window_started_at = CASE
                        WHEN mcp_context_usage.window_started_at < :windowCutoff
                        THEN :now ELSE mcp_context_usage.window_started_at END,
                    call_count = CASE
                        WHEN mcp_context_usage.window_started_at < :windowCutoff
                        THEN 1 ELSE mcp_context_usage.call_count + 1 END
                WHERE mcp_context_usage.window_started_at < :windowCutoff
                   OR mcp_context_usage.call_count < :limit
                RETURNING call_count
                """, values, (rs, row) -> rs.getInt(1)).isEmpty();
    }
}
