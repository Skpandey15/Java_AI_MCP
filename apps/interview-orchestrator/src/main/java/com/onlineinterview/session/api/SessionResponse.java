package com.onlineinterview.session.api;

import com.onlineinterview.session.application.SessionService.SessionView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SessionResponse(UUID id, UUID assignmentId, String state,
        Instant startedAt, Instant expiresAt, Instant serverTime,
        List<QuestionResponse> questions, List<AnswerResponse> answers) {
    static SessionResponse from(SessionView view) {
        var session = view.session();
        return new SessionResponse(session.getId(), session.getAssignment().getId(),
                session.getState().name(), session.getStartedAt(), session.getExpiresAt(),
                Instant.now(), view.questions().stream().map(QuestionResponse::from).toList(),
                view.answers().stream().map(AnswerResponse::from).toList());
    }
}
