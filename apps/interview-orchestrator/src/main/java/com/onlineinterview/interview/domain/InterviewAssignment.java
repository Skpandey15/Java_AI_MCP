package com.onlineinterview.interview.domain;

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
import java.util.UUID;

@Entity
@Table(name = "interview_assignment")
public class InterviewAssignment {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_definition_id", nullable = false)
    private InterviewDefinition interviewDefinition;
    @Column(name = "candidate_id", nullable = false) private UUID candidateId;
    @Column(name = "starts_at", nullable = false) private Instant startsAt;
    @Column(name = "ends_at", nullable = false) private Instant endsAt;
    @Column(name = "max_attempts", nullable = false) private int maxAttempts;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private AssignmentStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected InterviewAssignment() {}

    public static InterviewAssignment schedule(InterviewDefinition definition, UUID candidateId,
            Instant startsAt, Instant endsAt, int maxAttempts) {
        if (definition.getStatus() != InterviewStatus.PUBLISHED) {
            throw new IllegalStateException("Interview must be published before assignment");
        }
        if (!endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Assignment end time must be after start time");
        }
        var assignment = new InterviewAssignment();
        assignment.id = UUID.randomUUID();
        assignment.interviewDefinition = definition;
        assignment.candidateId = candidateId;
        assignment.startsAt = startsAt;
        assignment.endsAt = endsAt;
        assignment.maxAttempts = maxAttempts;
        assignment.status = AssignmentStatus.SCHEDULED;
        assignment.createdAt = Instant.now();
        return assignment;
    }

    public UUID getId() { return id; }
    public InterviewDefinition getInterviewDefinition() { return interviewDefinition; }
    public UUID getCandidateId() { return candidateId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public int getMaxAttempts() { return maxAttempts; }
    public AssignmentStatus getStatus() { return status; }
}
