package com.onlineinterview.session.api;

import com.onlineinterview.session.domain.ManualQuestion;
import java.util.UUID;

public record QuestionResponse(UUID id, int order, String prompt, int maxScore) {
    public static QuestionResponse from(ManualQuestion question) {
        return new QuestionResponse(question.getId(), question.getOrder(),
                question.getPrompt(), question.getMaxScore());
    }
}
