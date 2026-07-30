package com.onlineinterview.knowledge.infrastructure;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class KnowledgeVectorStore {
    public static final int DIMENSIONS = 1536;
    private final NamedParameterJdbcTemplate jdbc;

    public KnowledgeVectorStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void store(UUID chunkId, List<Double> embedding) {
        String vector = vectorLiteral(embedding);
        int updated = jdbc.update("""
                UPDATE knowledge_chunk SET embedding = CAST(:embedding AS vector)
                WHERE id = :chunkId
                """, new MapSqlParameterSource()
                .addValue("embedding", vector).addValue("chunkId", chunkId));
        if (updated != 1) throw new IllegalStateException("Knowledge chunk not found");
    }

    public List<SearchHit> search(
            String ownerSubject, UUID collectionId, List<Double> embedding, int limit) {
        return search(ownerSubject, collectionId, embedding, limit, -1);
    }

    public List<SearchHit> search(String ownerSubject, UUID collectionId,
            List<Double> embedding, int limit, double minimumSimilarity) {
        if (limit < 1 || limit > 20) throw new IllegalArgumentException("Limit must be 1-20");
        if (!Double.isFinite(minimumSimilarity)
                || minimumSimilarity < -1 || minimumSimilarity > 1) {
            throw new IllegalArgumentException("Minimum similarity must be between -1 and 1");
        }
        var parameters = new MapSqlParameterSource()
                .addValue("embedding", vectorLiteral(embedding))
                .addValue("owner", ownerSubject)
                .addValue("collectionId", collectionId)
                .addValue("minimumSimilarity", minimumSimilarity)
                .addValue("limit", limit);
        return jdbc.query("""
                SELECT kc.id, kd.id AS document_id, kd.file_name, kc.chunk_index,
                       kc.content, 1 - (kc.embedding <=> CAST(:embedding AS vector)) AS score
                FROM knowledge_chunk kc
                JOIN knowledge_document kd ON kd.id = kc.document_id
                JOIN knowledge_collection col ON col.id = kd.collection_id
                WHERE col.owner_subject = :owner
                  AND col.id = :collectionId
                  AND kd.status = 'READY'
                  AND kc.embedding IS NOT NULL
                  AND 1 - (kc.embedding <=> CAST(:embedding AS vector)) >= :minimumSimilarity
                ORDER BY kc.embedding <=> CAST(:embedding AS vector)
                LIMIT :limit
                """, parameters, (rs, row) -> new SearchHit(
                        rs.getObject("id", UUID.class),
                        rs.getObject("document_id", UUID.class),
                        rs.getString("file_name"),
                        rs.getInt("chunk_index"),
                        rs.getString("content"),
                        rs.getDouble("score")));
    }

    static String vectorLiteral(List<Double> embedding) {
        if (embedding == null || embedding.size() != DIMENSIONS
                || embedding.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
            throw new IllegalArgumentException("Embedding must contain 1536 finite values");
        }
        return embedding.stream().map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    public record SearchHit(UUID chunkId, UUID documentId, String fileName,
            int chunkIndex, String content, double score) {}
}
