package com.onlineinterview.mcp.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

class McpContextUsageStoreTest {
    @Test
    void reportsWhetherAtomicDatabaseQuotaWasConsumed() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var store = new McpContextUsageStore(jdbc);
        when(jdbc.query(anyString(), any(Map.class), any(RowMapper.class)))
                .thenReturn(List.of(1), List.of());

        assertThat(store.consume(UUID.randomUUID(), 5, Instant.now())).isTrue();
        assertThat(store.consume(UUID.randomUUID(), 5, Instant.now())).isFalse();
    }

    @Test
    void usesAtomicRedisQuotaWhenAvailable() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var provider = mock(ObjectProvider.class);
        var redis = mock(StringRedisTemplate.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenReturn(1L, 0L);
        var store = new McpContextUsageStore(jdbc, provider);

        assertThat(store.consume(UUID.randomUUID(), 5, Instant.now())).isTrue();
        assertThat(store.consume(UUID.randomUUID(), 5, Instant.now())).isFalse();
        verifyNoInteractions(jdbc);
    }

    @Test
    void failsClosedWhenRedisIsUnavailable() {
        var jdbc = mock(NamedParameterJdbcTemplate.class);
        var provider = mock(ObjectProvider.class);
        var redis = mock(StringRedisTemplate.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        when(redis.execute(any(RedisScript.class), anyList(), any(), any()))
                .thenThrow(new DataAccessResourceFailureException("down"));
        assertThat(new McpContextUsageStore(jdbc, provider)
                .consume(UUID.randomUUID(), 5, Instant.now())).isFalse();
        verifyNoInteractions(jdbc);
    }
}
