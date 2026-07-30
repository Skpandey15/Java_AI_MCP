package com.onlineinterview.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.ResultSet;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class KnowledgeVectorStoreTest {
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final KnowledgeVectorStore store = new KnowledgeVectorStore(jdbc);

    @Test
    void validatesAndStoresEmbedding() {
        var id = UUID.randomUUID();
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1, 0);

        store.store(id, vector(0.25));
        assertThatThrownBy(() -> store.store(id, vector(0.25)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> store.store(id, List.of(1.0)))
                .isInstanceOf(IllegalArgumentException.class);
        var invalid = vector(0.0);
        invalid.set(2, Double.NaN);
        assertThatThrownBy(() -> store.store(id, invalid))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void searchesWithOwnerCollectionStatusAndLimitFilters() throws Exception {
        var chunkId = UUID.randomUUID();
        var documentId = UUID.randomUUID();
        var resultSet = mock(ResultSet.class);
        when(resultSet.getObject("id", UUID.class)).thenReturn(chunkId);
        when(resultSet.getObject("document_id", UUID.class)).thenReturn(documentId);
        when(resultSet.getString("file_name")).thenReturn("source.md");
        when(resultSet.getInt("chunk_index")).thenReturn(3);
        when(resultSet.getString("content")).thenReturn("grounded content");
        when(resultSet.getDouble("score")).thenReturn(0.92);
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> List.of(
                        ((RowMapper<KnowledgeVectorStore.SearchHit>) invocation.getArgument(2))
                                .mapRow(resultSet, 0)));

        var hits = store.search("owner", UUID.randomUUID(), vector(0.5), 5);

        assertThat(hits).singleElement().satisfies(hit -> {
            assertThat(hit.chunkId()).isEqualTo(chunkId);
            assertThat(hit.documentId()).isEqualTo(documentId);
            assertThat(hit.fileName()).isEqualTo("source.md");
            assertThat(hit.chunkIndex()).isEqualTo(3);
            assertThat(hit.content()).isEqualTo("grounded content");
            assertThat(hit.score()).isEqualTo(0.92);
        });
        assertThatThrownBy(() -> store.search("owner", UUID.randomUUID(), vector(0), 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search("owner", UUID.randomUUID(), vector(0), 21))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.search(
                "owner", UUID.randomUUID(), vector(0), 5, 1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static java.util.ArrayList<Double> vector(double value) {
        return new java.util.ArrayList<>(
                Collections.nCopies(KnowledgeVectorStore.DIMENSIONS, value));
    }
}
