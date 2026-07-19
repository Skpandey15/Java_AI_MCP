package com.onlineinterview.review.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SubmissionDetailResponse(
        UUID sessionId, String interviewTitle, String candidateName, String candidateEmail,
        Instant submittedAt, String reviewStatus, int objectiveScore, Integer totalScore,
        int maxScore, int passingPercentage, Integer percentage,
        String outcome, String feedback, List<ReviewQuestionResponse> questions) {
}
