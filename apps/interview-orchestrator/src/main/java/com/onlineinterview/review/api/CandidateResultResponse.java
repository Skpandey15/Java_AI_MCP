package com.onlineinterview.review.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CandidateResultResponse(
        UUID sessionId, String interviewTitle, Instant submittedAt, String reviewStatus,
        Integer totalScore, int maxScore, int passingPercentage, Integer percentage,
        String outcome, String feedback, List<CandidateAnswerResult> answers,
        String coachingFeedback) {
    public record CandidateAnswerResult(
            int order, String type, String prompt, String content,
            int maxScore, Integer awardedScore, String feedback, String modelAnswer) {
    }
}
