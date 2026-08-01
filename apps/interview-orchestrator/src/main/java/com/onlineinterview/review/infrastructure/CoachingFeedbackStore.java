package com.onlineinterview.review.infrastructure;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate-backed store for candidate coaching feedback (not a JPA entity, so it does
 *  not participate in Hibernate schema validation before the migration has run). */
@Repository
public class CoachingFeedbackStore {
    private final JdbcTemplate jdbc;

    public CoachingFeedbackStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertDraft(UUID sessionId, String content, boolean leakageSafe, List<String> flags) {
        jdbc.update("""
                INSERT INTO coaching_feedback
                    (session_id, content, status, leakage_safe, leakage_flags, created_at, approved_at)
                VALUES (?, ?, 'DRAFT', ?, ?, ?, NULL)
                ON CONFLICT (session_id) DO UPDATE SET
                    content = EXCLUDED.content, status = 'DRAFT',
                    leakage_safe = EXCLUDED.leakage_safe, leakage_flags = EXCLUDED.leakage_flags,
                    created_at = EXCLUDED.created_at, approved_at = NULL
                """, sessionId, content, leakageSafe, String.join("\n", flags), OffsetDateTime.now(ZoneOffset.UTC));
    }

    public int approve(UUID sessionId) {
        return jdbc.update(
                "UPDATE coaching_feedback SET status = 'APPROVED', approved_at = ? WHERE session_id = ?",
                OffsetDateTime.now(ZoneOffset.UTC), sessionId);
    }

    public Optional<Coaching> find(UUID sessionId) {
        return jdbc.query(
                "SELECT content, status, leakage_safe, leakage_flags FROM coaching_feedback WHERE session_id = ?",
                rs -> rs.next() ? Optional.of(new Coaching(
                        rs.getString("content"), rs.getString("status"),
                        rs.getBoolean("leakage_safe"),
                        rs.getString("leakage_flags").isBlank()
                                ? List.of() : List.of(rs.getString("leakage_flags").split("\n"))))
                        : Optional.empty(),
                sessionId);
    }

    public record Coaching(String content, String status, boolean leakageSafe, List<String> flags) {
        public boolean approved() { return "APPROVED".equals(status); }
    }
}
