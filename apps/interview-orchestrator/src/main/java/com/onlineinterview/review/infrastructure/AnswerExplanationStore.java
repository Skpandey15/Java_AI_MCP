package com.onlineinterview.review.infrastructure;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate-backed store for the AI "detailed answer" — a candidate-answer-aware
 *  explanation of why one submitted answer fell short. Keyed by answer (not question) so the
 *  critique is never reused across candidates. Not a JPA entity, so it does not participate in
 *  Hibernate schema validation before migration. */
@Repository
public class AnswerExplanationStore {
    private final NamedParameterJdbcTemplate jdbc;

    public AnswerExplanationStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Detailed-answer content by answer id for the given answers (missing ones are absent). */
    public Map<UUID, String> findByAnswerIds(Collection<UUID> answerIds) {
        var result = new HashMap<UUID, String>();
        if (answerIds.isEmpty()) return result;
        jdbc.query("SELECT answer_id, content FROM answer_explanation "
                        + "WHERE answer_id IN (:ids)", Map.of("ids", answerIds),
                rs -> { result.put(UUID.fromString(rs.getString("answer_id")),
                        rs.getString("content")); });
        return result;
    }

    public void upsert(UUID answerId, String content) {
        jdbc.update("""
                INSERT INTO answer_explanation (answer_id, content, created_at)
                VALUES (:id, :content, :now)
                ON CONFLICT (answer_id) DO UPDATE SET
                    content = EXCLUDED.content, created_at = EXCLUDED.created_at
                """, Map.of("id", answerId, "content", content,
                "now", OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
