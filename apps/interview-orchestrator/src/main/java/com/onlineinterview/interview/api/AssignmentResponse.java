package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.InterviewAssignment;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id, UUID interviewId, String interviewTitle, UUID candidateId,
        Instant startsAt, Instant endsAt, int maxAttempts, String status) {
    static AssignmentResponse from(InterviewAssignment assignment) {
        return new AssignmentResponse(assignment.getId(),
                assignment.getInterviewDefinition().getId(),
                assignment.getInterviewDefinition().getTitle(),
                assignment.getCandidateId(), assignment.getStartsAt(),
                assignment.getEndsAt(), assignment.getMaxAttempts(),
                assignment.getStatus().name());
    }
}
