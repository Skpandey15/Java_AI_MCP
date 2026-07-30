package com.onlineinterview.interview.api;

import com.onlineinterview.interview.domain.InterviewDefinition;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InterviewResponse(
        UUID id, String title, String description, List<String> skills,
        String difficulty, String questionMode, int durationMinutes,
        int questionCount, QuestionCompositionResponse questionComposition,
        int passingPercentage, UUID knowledgeCollectionId, String status, Instant createdAt) {
    static InterviewResponse from(InterviewDefinition definition) {
        return new InterviewResponse(definition.getId(), definition.getTitle(),
                definition.getDescription(), definition.getSkills(),
                definition.getDifficulty().name(), definition.getQuestionMode().name(),
                definition.getDurationMinutes(), definition.getQuestionCount(),
                QuestionCompositionResponse.from(definition.getQuestionComposition()),
                definition.getPassingPercentage(), definition.getKnowledgeCollectionId(),
                definition.getStatus().name(), definition.getCreatedAt());
    }

    public record QuestionCompositionResponse(
            int mcqSingle, int mcqMultiple, int shortText, int longText) {
        static QuestionCompositionResponse from(
                com.onlineinterview.interview.domain.QuestionComposition composition) {
            return new QuestionCompositionResponse(
                    composition.mcqSingle(), composition.mcqMultiple(),
                    composition.shortText(), composition.longText());
        }
    }
}
