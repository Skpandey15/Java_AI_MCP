package com.onlineinterview.ai;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JdbcTemplate-backed store for async composition jobs. Status updates auto-commit
 *  independently of the work transaction, so a failed compose still records FAILED even
 *  when its transaction rolls back. Not a JPA entity. */
@Repository
public class CompositionJobStore {
    private final JdbcTemplate jdbc;

    public CompositionJobStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void create(UUID id, UUID interviewId, String ownerSubject, UUID requestId) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO composition_job
                    (id, interview_definition_id, owner_subject, generation_request_id,
                     status, question_count, rounds, error, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'PENDING', 0, 0, NULL, ?, ?)
                """, id, interviewId, ownerSubject, requestId, now, now);
    }

    public void markRunning(UUID id) {
        jdbc.update("UPDATE composition_job SET status = 'RUNNING', updated_at = ? WHERE id = ?",
                OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    public void markSucceeded(UUID id, int questionCount, int rounds) {
        jdbc.update("UPDATE composition_job SET status = 'SUCCEEDED', question_count = ?, "
                        + "rounds = ?, updated_at = ? WHERE id = ?",
                questionCount, rounds, OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    public void markFailed(UUID id, String error) {
        jdbc.update("UPDATE composition_job SET status = 'FAILED', error = ?, updated_at = ? "
                        + "WHERE id = ?",
                error == null ? "Composition failed" : error.substring(0, Math.min(error.length(), 500)),
                OffsetDateTime.now(ZoneOffset.UTC), id);
    }

    public Optional<Job> findByRequestId(UUID requestId) {
        return one("generation_request_id = ?", requestId);
    }

    public Optional<Job> findByIdAndOwner(UUID id, String ownerSubject) {
        return one("id = ? AND owner_subject = ?", id, ownerSubject);
    }

    private Optional<Job> one(String where, Object... args) {
        return jdbc.query("SELECT id, interview_definition_id, status, question_count, rounds, error "
                + "FROM composition_job WHERE " + where,
                rs -> rs.next() ? Optional.of(new Job(
                        UUID.fromString(rs.getString("id")),
                        UUID.fromString(rs.getString("interview_definition_id")),
                        rs.getString("status"), rs.getInt("question_count"),
                        rs.getInt("rounds"), rs.getString("error"))) : Optional.empty(),
                args);
    }

    public record Job(UUID id, UUID interviewId, String status, int questionCount,
            int rounds, String error) {}
}
