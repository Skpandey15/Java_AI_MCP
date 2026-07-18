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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionService {
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
            if (existing.get().getState() == SessionState.IN_PROGRESS) return existing.get();
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
        return sessions.save(InterviewSession.start(assignment, candidateId, now));
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
        Instant now = Instant.now();
        var existing = answers.findBySessionIdAndQuestionId(sessionId, questionId);
        if (existing.isEmpty()) {
            if (expectedVersion != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer version is stale");
            }
            return answers.saveAndFlush(InterviewAnswer.create(session, question, content, now));
        }
        try {
            existing.get().update(content, expectedVersion, now);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        return answers.saveAndFlush(existing.get());
    }

    @Transactional
    public SessionView submit(String subject, UUID sessionId) {
        var session = ownedSession(subject, sessionId);
        try {
            session.submit(Instant.now());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        return view(session);
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
        var answerList = answers.findBySessionId(session.getId());
        return new SessionView(session, questionList, answerList);
    }

    public record SessionView(InterviewSession session,
            List<com.onlineinterview.session.domain.ManualQuestion> questions,
            List<InterviewAnswer> answers) {}
}
