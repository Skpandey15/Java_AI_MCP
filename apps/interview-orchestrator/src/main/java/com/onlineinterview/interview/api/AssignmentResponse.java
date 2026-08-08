package com.onlineinterview.interview.api;

import com.onlineinterview.interview.application.InterviewService.CandidateAssignmentView;
import com.onlineinterview.interview.domain.InterviewAssignment;
import java.time.Instant;
import java.util.UUID;

public record AssignmentResponse(
        UUID id, UUID interviewId, String interviewTitle, UUID candidateId,
        Instant startsAt, Instant endsAt, int maxAttempts, String status,
        UUID sessionId, String sessionState, String reviewStatus, String questionMode) {
    static AssignmentResponse from(InterviewAssignment assignment) {
        return from(assignment, null, null, null);
    }

    static AssignmentResponse from(CandidateAssignmentView view) {
        return from(
                view.assignment(),
                view.sessionId(),
                view.sessionState() == null ? null : view.sessionState().name(),
                view.reviewStatus() == null ? null : view.reviewStatus().name());
    }

    private static AssignmentResponse from(
            InterviewAssignment assignment, UUID sessionId,
            String sessionState, String reviewStatus) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getInterviewDefinition().getId(),
                assignment.getInterviewDefinition().getTitle(),
                assignment.getCandidateId(),
                assignment.getStartsAt(),
                assignment.getEndsAt(),
                assignment.getMaxAttempts(),
                assignment.getStatus().name(),
                sessionId,
                sessionState,
                reviewStatus,
                assignment.getInterviewDefinition().getQuestionMode().name());
    }
}
