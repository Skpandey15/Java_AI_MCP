package com.onlineinterview.review.api;

import java.time.Instant;
import java.util.UUID;

public record SubmissionSummaryResponse(
        UUID sessionId, String interviewTitle, String candidateName, String candidateEmail,
        Instant submittedAt, String reviewStatus, Integer totalScore, int maxScore,
        Integer percentage, String outcome) {
}
