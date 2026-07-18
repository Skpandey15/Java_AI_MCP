package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.InterviewDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewResponse(
        UUID id, String title, String description, List<String> skills,
        String difficulty, String questionMode, int durationMinutes,
        int questionCount, String status, Instant createdAt) {
    static InterviewResponse from(InterviewDefinition definition) {
        return new InterviewResponse(definition.getId(), definition.getTitle(),
                definition.getDescription(), definition.getSkills(),
                definition.getDifficulty().name(), definition.getQuestionMode().name(),
                definition.getDurationMinutes(), definition.getQuestionCount(),
                definition.getStatus().name(), definition.getCreatedAt());
    }
}
