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
    @Enumerated(EnumType.STRING) @Column(name = "review_status", nullable = false)
    private ReviewStatus reviewStatus;
    @Column(name = "objective_score", nullable = false) private int objectiveScore;
    @Column(name = "total_score") private Integer totalScore;
    @Enumerated(EnumType.STRING) @Column(name = "result_outcome")
    private ResultOutcome resultOutcome;
    @Column(name = "review_feedback", length = 4000) private String reviewFeedback;
    @Column(name = "reviewed_at") private Instant reviewedAt;
    @Column(name = "reviewer_subject") private String reviewerSubject;
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
        session.reviewStatus = ReviewStatus.NOT_SUBMITTED;
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
        reviewStatus = ReviewStatus.PENDING_REVIEW;
    }

    public void recordObjectiveScore(int score) {
        if (state != SessionState.SUBMITTED) {
            throw new IllegalStateException("Only submitted sessions can be scored");
        }
        objectiveScore = score;
    }

    public void finalizeReview(int score, int maxScore, int passingPercentage,
            String feedback, String reviewer, Instant now) {
        if (state != SessionState.SUBMITTED || reviewStatus != ReviewStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only pending submissions can be finalized");
        }
        if (maxScore <= 0 || score < 0 || score > maxScore) {
            throw new IllegalArgumentException("Final score must be between 0 and the maximum score");
        }
        if (passingPercentage < 1 || passingPercentage > 100) {
            throw new IllegalArgumentException("Passing percentage must be between 1 and 100");
        }
        totalScore = score;
        resultOutcome = (long) score * 100 >= (long) maxScore * passingPercentage
                ? ResultOutcome.PASSED : ResultOutcome.NOT_SELECTED;
        reviewFeedback = feedback;
        reviewerSubject = reviewer;
        reviewedAt = now;
        reviewStatus = ReviewStatus.REVIEWED;
    }

    public UUID getId() { return id; }
    public InterviewAssignment getAssignment() { return assignment; }
    public UUID getCandidateId() { return candidateId; }
    public SessionState getState() { return state; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public ReviewStatus getReviewStatus() { return reviewStatus; }
    public int getObjectiveScore() { return objectiveScore; }
    public Integer getTotalScore() { return totalScore; }
    public ResultOutcome getResultOutcome() { return resultOutcome; }
    public String getReviewFeedback() { return reviewFeedback; }
    public Instant getReviewedAt() { return reviewedAt; }
}
