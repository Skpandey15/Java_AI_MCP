package com.onlineinterview.interview.application;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.InterviewStatus;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.domain.QuestionComposition;
import com.onlineinterview.interview.infrastructure.InterviewAssignmentRepository;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeCollectionRepository;
import com.onlineinterview.messaging.application.OutboxService;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.domain.UserStatus;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import com.onlineinterview.session.domain.ReviewStatus;
import com.onlineinterview.session.domain.SessionState;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class InterviewService {
    private static final Logger log = LoggerFactory.getLogger(InterviewService.class);
    private final InterviewDefinitionRepository definitions;
    private final InterviewAssignmentRepository assignments;
    private final UserProfileRepository profiles;
    private final ManualQuestionRepository questions;
    private final InterviewSessionRepository sessions;
    private final KnowledgeCollectionRepository knowledgeCollections;
    private final OutboxService outbox;

    @Autowired
    public InterviewService(InterviewDefinitionRepository definitions,
            InterviewAssignmentRepository assignments, UserProfileRepository profiles,
            ManualQuestionRepository questions, InterviewSessionRepository sessions,
            KnowledgeCollectionRepository knowledgeCollections, OutboxService outbox) {
        this.definitions = definitions;
        this.assignments = assignments;
        this.profiles = profiles;
        this.questions = questions;
        this.sessions = sessions;
        this.knowledgeCollections = knowledgeCollections;
        this.outbox = outbox;
    }

    InterviewService(InterviewDefinitionRepository definitions,
            InterviewAssignmentRepository assignments, UserProfileRepository profiles,
            ManualQuestionRepository questions, InterviewSessionRepository sessions,
            KnowledgeCollectionRepository knowledgeCollections) {
        this(definitions, assignments, profiles, questions, sessions, knowledgeCollections, null);
    }

    @Transactional
    public InterviewDefinition create(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage) {
        return create(ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, passingPercentage,
                QuestionComposition.allLongText(questionCount));
    }

    @Transactional
    public InterviewDefinition create(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage,
            QuestionComposition composition) {
        return create(ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, passingPercentage, composition, null);
    }

    @Transactional
    public InterviewDefinition create(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage,
            QuestionComposition composition, UUID knowledgeCollectionId) {
        if (composition.total() != questionCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Question composition total must equal question count");
        }
        if (knowledgeCollectionId != null && knowledgeCollections.findById(knowledgeCollectionId)
                .filter(collection -> collection.getOwnerSubject().equals(ownerSubject)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Knowledge collection not found");
        }
        var definition = definitions.save(InterviewDefinition.draft(
                ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, passingPercentage, composition,
                knowledgeCollectionId));
        log.atInfo().addKeyValue("event", "interview.created")
                .addKeyValue("interviewId", definition.getId())
                .addKeyValue("questionMode", questionMode)
                .log("Interview draft created");
        return definition;
    }

    @Transactional(readOnly = true)
    public List<InterviewDefinition> listOwned(String ownerSubject) {
        return definitions.findByOwnerSubjectAndStatusNotOrderByCreatedAtDesc(
                ownerSubject, InterviewStatus.ARCHIVED);
    }

    @Transactional
    public void archive(String ownerSubject, UUID interviewId) {
        var definition = ownedDefinition(ownerSubject, interviewId);
        definition.archive();
        log.atInfo().addKeyValue("event", "interview.archived")
                .addKeyValue("interviewId", interviewId).log("Interview archived");
    }

    @Transactional(readOnly = true)
    public List<InterviewAssignment> assignmentsFor(String ownerSubject, UUID interviewId) {
        ownedDefinition(ownerSubject, interviewId);
        return assignments.findByInterviewDefinition_IdOrderByStartsAtDesc(interviewId);
    }

    @Transactional
    public void unassign(String ownerSubject, UUID interviewId, UUID assignmentId) {
        ownedDefinition(ownerSubject, interviewId);
        var assignment = assignments.findByIdAndInterviewDefinition_Id(assignmentId, interviewId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Assignment not found"));
        if (sessions.countByAssignmentId(assignmentId) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Candidate has already started this interview; it cannot be unassigned");
        }
        assignments.delete(assignment);
        log.atInfo().addKeyValue("event", "interview.unassigned")
                .addKeyValue("interviewId", interviewId)
                .addKeyValue("assignmentId", assignmentId).log("Candidate unassigned");
    }

    @Transactional
    public InterviewDefinition publish(String ownerSubject, UUID interviewId) {
        var definition = ownedDefinition(ownerSubject, interviewId);
        long savedQuestions = questions.countByInterviewDefinitionId(interviewId);
        if (savedQuestions != definition.getQuestionCount()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Publishing requires exactly " + definition.getQuestionCount()
                            + " saved questions; found " + savedQuestions);
        }
        var actualComposition = questions.findByInterviewDefinitionIdOrderByOrderAsc(interviewId)
                .stream().collect(Collectors.groupingBy(
                        question -> question.getType(), Collectors.counting()));
        var expectedComposition = definition.getQuestionComposition();
        var mismatch = expectedComposition.asMap().entrySet().stream()
                .filter(entry -> actualComposition.getOrDefault(entry.getKey(), 0L)
                        != entry.getValue().longValue())
                .findFirst();
        if (mismatch.isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Saved question types do not match the configured composition");
        }
        try {
            definition.publish();
            if (outbox != null) {
                outbox.record("INTERVIEW", definition.getId(), "interview.published",
                        java.util.Map.of("interviewId", definition.getId().toString()));
            }
            log.atInfo().addKeyValue("event", "interview.published")
                    .addKeyValue("interviewId", definition.getId())
                    .log("Interview published");
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        return definition;
    }

    @Transactional
    public InterviewAssignment assign(String ownerSubject, UUID interviewId, UUID candidateId,
            Instant startsAt, Instant endsAt, int maxAttempts) {
        var definition = ownedDefinition(ownerSubject, interviewId);
        var candidate = profiles.findById(candidateId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found"));
        var owner = profiles.findByIdentitySubject(ownerSubject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "Interviewer profile registration is incomplete"));
        if (!candidate.getTenantId().equals(owner.getTenantId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found");
        }
        if (candidate.getRole() != UserRole.CANDIDATE || candidate.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile is not an active candidate");
        }
        try {
            var assignment = assignments.save(InterviewAssignment.schedule(
                    definition, candidateId, startsAt, endsAt, maxAttempts));
            log.atInfo().addKeyValue("event", "interview.assigned")
                    .addKeyValue("interviewId", interviewId)
                    .addKeyValue("assignmentId", assignment.getId())
                    .addKeyValue("candidateId", candidateId)
                    .log("Candidate assigned to interview");
            return assignment;
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @Transactional(readOnly = true)
    public List<CandidateAssignmentView> candidateAssignments(String candidateSubject) {
        var candidate = profiles.findByIdentitySubject(candidateSubject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate profile registration is incomplete"));
        var latestSessionByAssignment = sessions.findByCandidateIdOrderByStartedAtDesc(candidate.getId())
                .stream().collect(Collectors.toMap(
                        session -> session.getAssignment().getId(),
                        Function.identity(),
                        (latest, ignored) -> latest));
        return assignments.findByCandidateIdOrderByStartsAtAsc(candidate.getId()).stream()
                .map(assignment -> {
                    var session = latestSessionByAssignment.get(assignment.getId());
                    return new CandidateAssignmentView(
                            assignment,
                            session == null ? null : session.getId(),
                            session == null ? null : session.getState(),
                            session == null ? null : session.getReviewStatus());
                }).toList();
    }

    public record CandidateAssignmentView(
            InterviewAssignment assignment, UUID sessionId,
            SessionState sessionState, ReviewStatus reviewStatus) {}

    private InterviewDefinition ownedDefinition(String ownerSubject, UUID interviewId) {
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        return definition;
    }
}
