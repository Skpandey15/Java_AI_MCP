package com.onlineinterview.session.domain;

import com.onlineinterview.interview.domain.InterviewAssignment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "interview_session")
public class InterviewSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private InterviewAssignment assignment;
    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private SessionState state;
    @Column(name = "started_at", nullable = false) private Instant startedAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Version private long version;

    protected InterviewSession() {}

    public static InterviewSession start(InterviewAssignment assignment, UUID candidateId, Instant now) {
        var durationExpiry = now.plus(
                assignment.getInterviewDefinition().getDurationMinutes(), ChronoUnit.MINUTES);
        var session = new InterviewSession();
        session.id = UUID.randomUUID();
        session.assignment = assignment;
        session.candidateId = candidateId;
        session.state = SessionState.IN_PROGRESS;
        session.startedAt = now;
        session.expiresAt = durationExpiry.isBefore(assignment.getEndsAt())
                ? durationExpiry : assignment.getEndsAt();
        return session;
    }

    public void enforceExpiry(Instant now) {
        if (state == SessionState.IN_PROGRESS && !now.isBefore(expiresAt)) {
            state = SessionState.EXPIRED;
        }
    }

    public void submit(Instant now) {
        enforceExpiry(now);
        if (state != SessionState.IN_PROGRESS) {
            throw new IllegalStateException("Only an active session can be submitted");
        }
        state = SessionState.SUBMITTED;
        submittedAt = now;
    }

    public UUID getId() { return id; }
    public InterviewAssignment getAssignment() { return assignment; }
    public UUID getCandidateId() { return candidateId; }
    public SessionState getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSubmittedAt() { return submittedAt; }
}
