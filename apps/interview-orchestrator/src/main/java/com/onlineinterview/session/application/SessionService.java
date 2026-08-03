package com.onlineinterview.session.application;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.infrastructure.InterviewAssignmentRepository;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import com.onlineinterview.session.domain.InterviewAnswer;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.domain.SessionState;
import com.onlineinterview.session.infrastructure.InterviewAnswerRepository;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private final InterviewAssignmentRepository assignments;
    private final InterviewSessionRepository sessions;
    private final ManualQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final UserProfileRepository profiles;

    public SessionService(InterviewAssignmentRepository assignments,
            InterviewSessionRepository sessions, ManualQuestionRepository questions,
            InterviewAnswerRepository answers, UserProfileRepository profiles) {
        this.assignments = assignments;
        this.sessions = sessions;
        this.questions = questions;
        this.answers = answers;
        this.profiles = profiles;
    }

    @Transactional
    public InterviewSession start(String subject, UUID assignmentId) {
        UUID candidateId = candidateId(subject);
        var existing = sessions.findFirstByAssignmentIdAndCandidateIdAndState(
                assignmentId, candidateId, SessionState.IN_PROGRESS);
        if (existing.isPresent()) {
            existing.get().enforceExpiry(Instant.now());
            if (existing.get().getState() == SessionState.IN_PROGRESS) {
                log.atInfo().addKeyValue("event", "session.resumed")
                        .addKeyValue("sessionId", existing.get().getId())
                        .addKeyValue("assignmentId", assignmentId)
                        .log("Interview session resumed");
                return existing.get();
            }
        }
        InterviewAssignment assignment = assignments.findByIdAndCandidateId(assignmentId, candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment not found"));
        Instant now = Instant.now();
        if (now.isBefore(assignment.getStartsAt()) || !now.isBefore(assignment.getEndsAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Assignment is outside its allowed time window");
        }
        if (questions.countByInterviewDefinitionId(assignment.getInterviewDefinition().getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Interview has no manual questions");
        }
        if (sessions.countByAssignmentId(assignmentId) >= assignment.getMaxAttempts()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Maximum attempts reached");
        }
        InterviewSession session;
        try {
            session = sessions.saveAndFlush(InterviewSession.start(assignment, candidateId, now));
        } catch (DataIntegrityViolationException race) {
            // A concurrent start won the uq_active_assignment_session index. Return a clean
            // 409 so the client retries and resumes the already-active session, rather than a 500.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A session for this assignment is already starting; please retry", race);
        }
        log.atInfo().addKeyValue("event", "session.started")
                .addKeyValue("sessionId", session.getId())
                .addKeyValue("assignmentId", assignmentId)
                .addKeyValue("candidateId", candidateId)
                .log("Interview session started");
        return session;
    }

    @Transactional
    public SessionView load(String subject, UUID sessionId) {
        var session = ownedSession(subject, sessionId);
        session.enforceExpiry(Instant.now());
        return view(session);
    }

    @Transactional
    public InterviewAnswer autosave(String subject, UUID sessionId, UUID questionId,
            String content, long expectedVersion) {
        var session = ownedSession(subject, sessionId);
        session.enforceExpiry(Instant.now());
        if (session.getState() != SessionState.IN_PROGRESS) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is no longer active");
        }
        var question = questions.findById(questionId)
                .filter(q -> q.getInterviewDefinition().getId()
                        .equals(session.getAssignment().getInterviewDefinition().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Question not found"));
        try {
            question.validateAnswer(content);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        Instant now = Instant.now();
        var existing = answers.findBySession_IdAndQuestion_Id(sessionId, questionId);
        if (existing.isEmpty()) {
            if (expectedVersion != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer version is stale");
            }
            var saved = answers.saveAndFlush(InterviewAnswer.create(session, question, content, now));
            log.atInfo().addKeyValue("event", "answer.autosaved")
                    .addKeyValue("sessionId", sessionId)
                    .addKeyValue("questionId", questionId)
                    .addKeyValue("answerId", saved.getId())
                    .log("Answer autosaved");
            return saved;
        }
        try {
            existing.get().update(content, expectedVersion, now);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        var saved = answers.saveAndFlush(existing.get());
        log.atInfo().addKeyValue("event", "answer.autosaved")
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("questionId", questionId)
                .addKeyValue("answerId", saved.getId())
                .log("Answer autosaved");
        return saved;
    }

    @Transactional
    public SessionView submit(String subject, UUID sessionId) {
        var session = ownedSession(subject, sessionId);
        try {
            session.submit(Instant.now());
            applyObjectiveScore(session);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        log.atInfo().addKeyValue("event", "session.submitted")
                .addKeyValue("sessionId", sessionId)
                .log("Interview session submitted");
        return view(session);
    }

    /** Scores auto-gradable (MCQ) answers and records the objective total on the session. */
    private void applyObjectiveScore(InterviewSession session) {
        var submittedAnswers = answers.findBySession_Id(session.getId());
        submittedAnswers.forEach(InterviewAnswer::scoreObjective);
        int objectiveScore = submittedAnswers.stream()
                .filter(InterviewAnswer::isAutoScored)
                .map(InterviewAnswer::getAwardedScore)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        session.recordObjectiveScore(objectiveScore);
    }

    /** Safety net: auto-submit sessions whose time expired without an explicit submit, so they
     *  are scored and queued for review instead of dead-ending. Runs on a fixed schedule; the
     *  candidate UI also auto-submits at 0 for immediacy. */
    @Transactional
    @Scheduled(fixedDelayString = "${app.session.expiry-sweep-ms:60000}")
    public void finalizeExpiredSessions() {
        var now = Instant.now();
        var expired = sessions.findByExpiresAtBeforeAndReviewStatus(
                now, com.onlineinterview.session.domain.ReviewStatus.NOT_SUBMITTED);
        for (var session : expired) {
            if (session.autoSubmitIfExpired(now)) {
                applyObjectiveScore(session);
                log.atInfo().addKeyValue("event", "session.auto_submitted")
                        .addKeyValue("sessionId", session.getId())
                        .log("Expired interview session auto-submitted for review");
            }
        }
    }

    private InterviewSession ownedSession(String subject, UUID sessionId) {
        return sessions.findByIdAndCandidateId(sessionId, candidateId(subject))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
    }

    private UUID candidateId(String subject) {
        return profiles.findByIdentitySubject(subject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate profile registration is incomplete"))
                .getId();
    }

    private SessionView view(InterviewSession session) {
        var questionList = questions.findByInterviewDefinitionIdOrderByOrderAsc(
                session.getAssignment().getInterviewDefinition().getId());
        var answerList = answers.findBySession_Id(session.getId());
        return new SessionView(session, questionList, answerList);
    }

    public record SessionView(InterviewSession session,
            List<com.onlineinterview.session.domain.ManualQuestion> questions,
            List<InterviewAnswer> answers) {}
}
