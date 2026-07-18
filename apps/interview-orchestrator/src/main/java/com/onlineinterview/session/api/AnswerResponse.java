package com.onlineinterview.session.api;

import com.onlineinterview.session.domain.InterviewAnswer;
import java.time.Instant;
import java.util.UUID;

public record AnswerResponse(UUID id, UUID questionId, String content, Instant updatedAt, long version) {
    static AnswerResponse from(InterviewAnswer answer) {
        return new AnswerResponse(answer.getId(), answer.getQuestionId(),
                answer.getContent(), answer.getUpdatedAt(), answer.getVersion());
    }
}
