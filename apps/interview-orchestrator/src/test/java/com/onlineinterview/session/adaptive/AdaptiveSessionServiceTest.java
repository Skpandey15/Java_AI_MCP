package com.onlineinterview.session.adaptive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.session.application.SessionService;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class AdaptiveSessionServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-08T10:00:00Z");
    private final SessionService sessionService = mock(SessionService.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final ManualQuestionRepository questions = mock(ManualQuestionRepository.class);
    private final AdaptiveSessionStateRepository states = mock(AdaptiveSessionStateRepository.class);
    private final AdaptiveTurnRepository turns = mock(AdaptiveTurnRepository.class);
    private final AdaptiveInterviewClient client = mock(AdaptiveInterviewClient.class);
    private final AdaptiveProperties properties = new AdaptiveProperties();

    private AdaptiveSessionService service() {
        return new AdaptiveSessionService(sessionService, sessions, questions, states, turns,
                client, properties, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private InterviewSession session(UUID sessionId, QuestionMode mode) {
        var session = mock(InterviewSession.class);
        var assignment = mock(InterviewAssignment.class);
        var definition = mock(InterviewDefinition.class);
        when(session.getId()).thenReturn(sessionId);
        when(session.getAssignment()).thenReturn(assignment);
        when(assignment.getInterviewDefinition()).thenReturn(definition);
        when(definition.getQuestionMode()).thenReturn(mode);
        when(definition.getId()).thenReturn(UUID.randomUUID());
        when(definition.getSkills()).thenReturn(List.of("Concurrency"));
        when(definition.getDifficulty()).thenReturn(InterviewDifficulty.HARD);
        when(definition.getPassingPercentage()).thenReturn(70);
        return session;
    }

    @Test
    void startRejectedWhenFeatureDisabled() {
        properties.setEnabled(false);
        assertThatThrownBy(() -> service().start("candidate", UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void startRejectedForNonAdaptiveInterview() {
        properties.setEnabled(true);
        var assignmentId = UUID.randomUUID();
        var session = session(UUID.randomUUID(), QuestionMode.RAG);
        when(sessionService.start("candidate", assignmentId)).thenReturn(session);
        assertThatThrownBy(() -> service().start("candidate", assignmentId))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void startAsksTheFirstQuestion() {
        properties.setEnabled(true);
        var assignmentId = UUID.randomUUID();
        var sessionId = UUID.randomUUID();
        var session = session(sessionId, QuestionMode.ADAPTIVE);
        when(sessionService.start("candidate", assignmentId)).thenReturn(session);
        when(states.findById(sessionId)).thenReturn(Optional.empty());
        when(states.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(questions.findByInterviewDefinitionIdOrderByOrderAsc(any())).thenReturn(List.of());
        when(turns.findBySessionIdOrderByOrdinalAsc(sessionId)).thenReturn(List.of());
        when(turns.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(client.nextTurn(any())).thenReturn(new AdaptiveInterviewClient.NextTurnResponse(
                "ASK", "probe", new AdaptiveInterviewClient.AskedQuestion(
                        "Concurrency", "HARD", "GENERATED", null, "Explain the JMM.", List.of()),
                null, null, new AdaptiveInterviewClient.Usage(10, 5, 15, 0.0, 12)));
        var asked = AdaptiveTurn.ask(sessionId, 1, null, "Explain the JMM.",
                "Concurrency", "HARD", "GENERATED", "probe");
        when(turns.findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(sessionId))
                .thenReturn(Optional.of(asked));

        var view = service().start("candidate", assignmentId);

        assertThat(view.done()).isFalse();
        assertThat(view.turnsUsed()).isEqualTo(1);
        assertThat(view.currentQuestion().prompt()).isEqualTo("Explain the JMM.");
        verify(turns).saveAndFlush(any(AdaptiveTurn.class));
    }

    @Test
    void answerEvaluatesAndConcludesSubmittingTheSession() {
        properties.setEnabled(true);
        var sessionId = UUID.randomUUID();
        var session = session(sessionId, QuestionMode.ADAPTIVE);
        when(sessionService.load("candidate", sessionId))
                .thenReturn(new SessionService.SessionView(session, List.of(), List.of()));
        var state = AdaptiveSessionState.create(sessionId, 12, 60000);
        when(states.findById(sessionId)).thenReturn(Optional.of(state));
        when(states.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        var open = AdaptiveTurn.ask(sessionId, 1, UUID.randomUUID(), "Q1",
                "Concurrency", "HARD", "BANK", "r");
        when(turns.findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(sessionId))
                .thenReturn(Optional.of(open), Optional.empty());
        when(turns.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(turns.findBySessionIdOrderByOrdinalAsc(sessionId)).thenReturn(List.of(open));
        when(client.nextTurn(any())).thenReturn(new AdaptiveInterviewClient.NextTurnResponse(
                "CONCLUDE", "done", null,
                new AdaptiveInterviewClient.AnswerEvaluation("Concurrency", 80, 75, "solid"),
                new AdaptiveInterviewClient.FinalAssessment("great", List.of()),
                new AdaptiveInterviewClient.Usage(5, 5, 10, 0.0, 8)));

        var view = service().answer("candidate", sessionId, "my answer");

        assertThat(view.done()).isTrue();
        assertThat(view.currentQuestion()).isNull();
        assertThat(open.getScore()).isEqualTo(80);       // evaluation recorded on the answered turn
        assertThat(open.getAnswerText()).isEqualTo("my answer");
        verify(session).submit(any());
        verify(sessions).saveAndFlush(session);
    }

    @Test
    void answerRejectedWhenAlreadyConcluded() {
        properties.setEnabled(true);
        var sessionId = UUID.randomUUID();
        var session = session(sessionId, QuestionMode.ADAPTIVE);
        when(sessionService.load("candidate", sessionId))
                .thenReturn(new SessionService.SessionView(session, List.of(), List.of()));
        var state = AdaptiveSessionState.create(sessionId, 12, 60000);
        state.conclude(0);
        when(states.findById(sessionId)).thenReturn(Optional.of(state));

        assertThatThrownBy(() -> service().answer("candidate", sessionId, "x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void loadReturnsCurrentView() {
        properties.setEnabled(true);
        var sessionId = UUID.randomUUID();
        var session = session(sessionId, QuestionMode.ADAPTIVE);
        when(sessionService.load("candidate", sessionId))
                .thenReturn(new SessionService.SessionView(session, List.of(), List.of()));
        var state = AdaptiveSessionState.create(sessionId, 12, 60000);
        state.recordAsk(20);
        when(states.findById(sessionId)).thenReturn(Optional.of(state));
        var open = AdaptiveTurn.ask(sessionId, 1, null, "Q1", "Concurrency", "HARD", "BANK", "r");
        when(turns.findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(sessionId))
                .thenReturn(Optional.of(open));

        var view = service().load("candidate", sessionId);

        assertThat(view.turnsUsed()).isEqualTo(1);
        assertThat(view.currentQuestion().prompt()).isEqualTo("Q1");
    }
}
