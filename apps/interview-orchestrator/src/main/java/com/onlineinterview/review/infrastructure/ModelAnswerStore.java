package com.onlineinterview.review.infrastructure;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate-backed store for AI "answer key" model answers, keyed by question so a
 *  model answer is generated once and reused for every candidate on that interview. Not a
 *  JPA entity, so it does not participate in Hibernate schema validation before migration. */
@Repository
public class ModelAnswerStore {
    private final NamedParameterJdbcTemplate jdbc;

    public ModelAnswerStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Model-answer content by question id for the given questions (missing ones are absent). */
    public Map<UUID, String> findByQuestionIds(Collection<UUID> questionIds) {
        var result = new HashMap<UUID, String>();
        if (questionIds.isEmpty()) return result;
        jdbc.query("SELECT question_id, content FROM question_model_answer "
                        + "WHERE question_id IN (:ids)", Map.of("ids", questionIds),
                rs -> { result.put(UUID.fromString(rs.getString("question_id")),
                        rs.getString("content")); });
        return result;
    }

    public void upsert(UUID questionId, String content) {
        jdbc.update("""
                INSERT INTO question_model_answer (question_id, content, created_at)
                VALUES (:id, :content, :now)
                ON CONFLICT (question_id) DO UPDATE SET
                    content = EXCLUDED.content, created_at = EXCLUDED.created_at
                """, Map.of("id", questionId, "content", content,
                "now", OffsetDateTime.now(ZoneOffset.UTC)));
    }
}
