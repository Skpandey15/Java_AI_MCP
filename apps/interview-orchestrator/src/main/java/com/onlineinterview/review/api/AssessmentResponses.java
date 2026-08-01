package com.onlineinterview.review.api;

import java.util.List;
import java.util.UUID;

public final class AssessmentResponses {
    private AssessmentResponses() {}

    public record AnswerSuggestion(
            UUID answerId, int suggestedScore, double confidence, String justification) {}

    public record CoachingResponse(
            String status, boolean leakageSafe, List<String> leakageFlags, String content) {}
}
