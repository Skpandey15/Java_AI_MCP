package com.onlineinterview.review.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_audit_event")
public class ReviewAuditEvent {
    @Id private UUID id;
    @Column(name = "session_id", nullable = false) private UUID sessionId;
    @Column(name = "answer_id") private UUID answerId;
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false) private ReviewAuditEventType eventType;
    @Column(name = "actor_subject", nullable = false) private String actorSubject;
    @Column(name = "awarded_score") private Integer awardedScore;
    @Column(length = 4000) private String feedback;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    protected ReviewAuditEvent() {}

    public static ReviewAuditEvent answerScored(
            UUID sessionId, UUID answerId, String actorSubject,
            int awardedScore, String feedback, Instant occurredAt) {
        return create(sessionId, answerId, ReviewAuditEventType.ANSWER_SCORED,
                actorSubject, awardedScore, feedback, occurredAt);
    }

    public static ReviewAuditEvent reviewFinalized(
            UUID sessionId, String actorSubject, int totalScore,
            String feedback, Instant occurredAt) {
        return create(sessionId, null, ReviewAuditEventType.REVIEW_FINALIZED,
                actorSubject, totalScore, feedback, occurredAt);
    }

    private static ReviewAuditEvent create(
            UUID sessionId, UUID answerId, ReviewAuditEventType eventType,
            String actorSubject, Integer awardedScore, String feedback, Instant occurredAt) {
        var event = new ReviewAuditEvent();
        event.id = UUID.randomUUID();
        event.sessionId = sessionId;
        event.answerId = answerId;
        event.eventType = eventType;
        event.actorSubject = actorSubject;
        event.awardedScore = awardedScore;
        event.feedback = feedback;
        event.occurredAt = occurredAt;
        return event;
    }
}
