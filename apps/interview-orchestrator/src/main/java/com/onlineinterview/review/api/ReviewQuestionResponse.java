package com.onlineinterview.review.api;

import java.util.List;
import java.util.UUID;

public record ReviewQuestionResponse(
        UUID questionId, UUID answerId, int order, String type, String prompt,
        List<String> options, List<String> correctAnswers, String content,
        int maxScore, Integer awardedScore, String feedback, boolean autoScored) {
}
