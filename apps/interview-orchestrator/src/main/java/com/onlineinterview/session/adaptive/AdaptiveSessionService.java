package com.onlineinterview.session.adaptive;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.session.application.SessionService;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Durable outer loop of the Adaptive AI Interviewer. Reuses {@link SessionService} for
 *  candidate authorization + session lifecycle, brokers the (Phase-0) tools by reading the
 *  orchestrator's own repositories, calls the agent, and persists the resulting turn. All
 *  entry points are gated by {@code app.adaptive.enabled}. */
@Service
public class AdaptiveSessionService {
    private static final int MAX_CANDIDATES = 10;

    private final SessionService sessionService;
    private final InterviewSessionRepository sessions;
    private final ManualQuestionRepository questions;
    private final AdaptiveSessionStateRepository states;
    private final AdaptiveTurnRepository turns;
    private final AdaptiveInterviewClient client;
    private final AdaptiveProperties properties;
    private final Clock clock;

    @Autowired
    public AdaptiveSessionService(SessionService sessionService,
            InterviewSessionRepository sessions, ManualQuestionRepository questions,
            AdaptiveSessionStateRepository states,
            AdaptiveTurnRepository turns, AdaptiveInterviewClient client,
            AdaptiveProperties properties) {
        this(sessionService, sessions, questions, states, turns, client, properties,
                Clock.systemUTC());
    }

    AdaptiveSessionService(SessionService sessionService,
            InterviewSessionRepository sessions, ManualQuestionRepository questions,
            AdaptiveSessionStateRepository states,
            AdaptiveTurnRepository turns, AdaptiveInterviewClient client,
            AdaptiveProperties properties, Clock clock) {
        this.sessionService = sessionService;
        this.sessions = sessions;
        this.questions = questions;
        this.states = states;
        this.turns = turns;
        this.client = client;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AdaptiveView start(String subject, UUID assignmentId) {
        requireEnabled();
        var session = sessionService.start(subject, assignmentId);
        var definition = session.getAssignment().getInterviewDefinition();
        if (definition.getQuestionMode() != QuestionMode.ADAPTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview is not adaptive");
        }
        var state = states.findById(session.getId()).orElse(null);
        if (state == null) {
            state = states.saveAndFlush(AdaptiveSessionState.create(
                    session.getId(), properties.getMaxTurns(), properties.getTokenBudget()));
            runAgentTurn(session, definition, state);
        }
        return view(session.getId(), state);
    }

    @Transactional
    public AdaptiveView answer(String subject, UUID sessionId, String answerText) {
        requireEnabled();
        var session = sessionService.load(subject, sessionId).session();
        var state = adaptiveState(sessionId);
        if (state.isDone()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview already concluded");
        }
        var open = turns.findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(sessionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "No question is awaiting an answer"));
        open.recordAnswer(answerText == null ? "" : answerText);
        turns.saveAndFlush(open);
        runAgentTurn(session, session.getAssignment().getInterviewDefinition(), state);
        return view(sessionId, state);
    }

    @Transactional(readOnly = true)
    public AdaptiveView load(String subject, UUID sessionId) {
        requireEnabled();
        sessionService.load(subject, sessionId);
        return view(sessionId, adaptiveState(sessionId));
    }

    private void runAgentTurn(InterviewSession session, InterviewDefinition definition,
            AdaptiveSessionState state) {
        var existing = turns.findBySessionIdOrderByOrdinalAsc(session.getId());
        var response = client.nextTurn(buildRequest(session, definition, state, existing));
        int tokens = response.usage() == null ? 0 : response.usage().totalTokens();

        recordEvaluation(existing, response.lastAnswerEvaluation());

        if ("ASK".equals(response.action()) && response.question() != null) {
            var q = response.question();
            String skill = blank(q.skill()) ? firstSkill(definition) : q.skill();
            turns.saveAndFlush(AdaptiveTurn.ask(session.getId(), existing.size() + 1,
                    q.questionId(), q.prompt(), skill,
                    blank(q.difficulty()) ? definition.getDifficulty().name() : q.difficulty(),
                    "GENERATED".equals(q.source()) ? "GENERATED" : "BANK",
                    response.rationale()));
            state.recordAsk(tokens);
            states.saveAndFlush(state);
            return;
        }

        state.conclude(tokens);
        states.saveAndFlush(state);
        session.submit(Instant.now(clock));
        sessions.saveAndFlush(session);
    }

    private AdaptiveInterviewClient.NextTurnRequest buildRequest(InterviewSession session,
            InterviewDefinition definition, AdaptiveSessionState state, List<AdaptiveTurn> existing) {
        var transcript = existing.stream().filter(AdaptiveTurn::isAnswered)
                .map(t -> new AdaptiveInterviewClient.TranscriptEntry(
                        t.getSkill(), t.getQuestionText(), t.getAnswerText()))
                .toList();
        Set<UUID> asked = existing.stream().map(AdaptiveTurn::getQuestionId)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        String skill = firstSkill(definition);
        var candidates = questions.findByInterviewDefinitionIdOrderByOrderAsc(definition.getId())
                .stream().filter(q -> !asked.contains(q.getId())).limit(MAX_CANDIDATES)
                .map(q -> new AdaptiveInterviewClient.CandidateQuestion(
                        q.getId(), skill, definition.getDifficulty().name(), q.getPrompt()))
                .toList();
        return new AdaptiveInterviewClient.NextTurnRequest(
                session.getId(), definition.getId(), definition.getSkills(),
                definition.getDifficulty().name(), definition.getPassingPercentage(),
                transcript, List.of(), candidates, List.of(),
                new AdaptiveInterviewClient.TurnBudget(
                        state.turnsRemaining(),
                        Math.max(0, state.getTokenBudget() - state.getTokensUsed())));
    }

    private void recordEvaluation(List<AdaptiveTurn> existing,
            AdaptiveInterviewClient.AnswerEvaluation evaluation) {
        if (evaluation == null) {
            return;
        }
        existing.stream().filter(AdaptiveTurn::isAnswered).filter(t -> t.getScore() == null)
                .reduce((first, second) -> second)  // most recent answered-but-unevaluated turn
                .ifPresent(turn -> {
                    turn.recordEvaluation(evaluation.score(), evaluation.confidence());
                    turns.saveAndFlush(turn);
                });
    }

    private AdaptiveView view(UUID sessionId, AdaptiveSessionState state) {
        var open = turns.findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(sessionId)
                .map(t -> new CurrentQuestion(
                        t.getOrdinal(), t.getSkill(), t.getDifficulty(), t.getQuestionText()))
                .orElse(null);
        return new AdaptiveView(sessionId, state.getPhase(), state.getTurnsUsed(),
                state.getMaxTurns(), state.isDone(), open);
    }

    private AdaptiveSessionState adaptiveState(UUID sessionId) {
        return states.findById(sessionId).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Not an adaptive session"));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Adaptive interviews are off");
        }
    }

    private static String firstSkill(InterviewDefinition definition) {
        var skills = definition.getSkills();
        return skills.isEmpty() ? "general" : skills.get(0);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    public record AdaptiveView(UUID sessionId, String phase, int turnsUsed, int maxTurns,
            boolean done, CurrentQuestion currentQuestion) {}

    public record CurrentQuestion(int ordinal, String skill, String difficulty, String prompt) {}
}
