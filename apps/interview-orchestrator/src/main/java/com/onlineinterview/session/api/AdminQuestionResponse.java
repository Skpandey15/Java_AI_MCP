package com.onlineinterview.session.api;

import com.onlineinterview.session.domain.ManualQuestion;
import java.util.List;
import java.util.UUID;

public record AdminQuestionResponse(
        UUID id, int order, String prompt, int maxScore, String type,
        List<String> options, List<String> correctAnswers, String source,
        List<QuestionCitationResponse> citations) {
    public static AdminQuestionResponse from(ManualQuestion question) {
        return new AdminQuestionResponse(question.getId(), question.getOrder(),
                question.getPrompt(), question.getMaxScore(), question.getType().name(),
                question.getOptions(), question.getCorrectAnswers(), question.getSource().name(),
                question.getCitations().stream().map(QuestionCitationResponse::from).toList());
    }
}
