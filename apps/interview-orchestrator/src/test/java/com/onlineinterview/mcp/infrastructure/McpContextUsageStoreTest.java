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
}
