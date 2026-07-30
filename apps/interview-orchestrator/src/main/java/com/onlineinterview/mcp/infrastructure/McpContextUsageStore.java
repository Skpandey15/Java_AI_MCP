package com.onlineinterview.mcp.infrastructure;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class McpContextUsageStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(McpContextUsageStore.class);
    private static final DefaultRedisScript<Long> CONSUME = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if current > tonumber(ARGV[2]) then return 0 end
            return 1
            """, Long.class);
    private final NamedParameterJdbcTemplate jdbc;
    private final StringRedisTemplate redis;

    @Autowired
    public McpContextUsageStore(NamedParameterJdbcTemplate jdbc,
            ObjectProvider<StringRedisTemplate> redis) {
        this.jdbc = jdbc;
        this.redis = redis.getIfAvailable();
    }

    McpContextUsageStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.redis = null;
    }

    public boolean consume(UUID contextId, int limit, Instant now) {
        if (redis != null) {
            try {
                var accepted = redis.execute(CONSUME,
                        List.of("mcp:quota:" + contextId), "60000", Integer.toString(limit));
                return Long.valueOf(1).equals(accepted);
            } catch (DataAccessException exception) {
                LOGGER.warn("Redis quota store unavailable; denying MCP call");
                return false;
            }
        }
        return consumeFromDatabase(contextId, limit, now);
    }

    private boolean consumeFromDatabase(UUID contextId, int limit, Instant now) {
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
