package com.onlineinterview.interview.application;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewAssignmentRepository;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.domain.UserStatus;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InterviewService {
    private final InterviewDefinitionRepository definitions;
    private final InterviewAssignmentRepository assignments;
    private final UserProfileRepository profiles;
    private final ManualQuestionRepository questions;

    public InterviewService(InterviewDefinitionRepository definitions,
            InterviewAssignmentRepository assignments, UserProfileRepository profiles,
            ManualQuestionRepository questions) {
        this.definitions = definitions;
        this.assignments = assignments;
        this.profiles = profiles;
        this.questions = questions;
    }

    @Transactional
    public InterviewDefinition create(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount) {
        return definitions.save(InterviewDefinition.draft(ownerSubject, title, description,
                skills, difficulty, questionMode, durationMinutes, questionCount));
    }

    @Transactional(readOnly = true)
    public List<InterviewDefinition> listOwned(String ownerSubject) {
        return definitions.findByOwnerSubjectOrderByCreatedAtDesc(ownerSubject);
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
        try {
            definition.publish();
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
        if (candidate.getRole() != UserRole.CANDIDATE || candidate.getStatus() != UserStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Profile is not an active candidate");
        }
        try {
            return assignments.save(InterviewAssignment.schedule(
                    definition, candidateId, startsAt, endsAt, maxAttempts));
        } catch (IllegalStateException | IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
    }

    @Transactional(readOnly = true)
    public List<InterviewAssignment> candidateAssignments(String candidateSubject) {
        var candidate = profiles.findByIdentitySubject(candidateSubject)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Candidate profile registration is incomplete"));
        return assignments.findByCandidateIdOrderByStartsAtAsc(candidate.getId());
    }

    private InterviewDefinition ownedDefinition(String ownerSubject, UUID interviewId) {
        var definition = definitions.findById(interviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found"));
        if (!definition.getOwnerSubject().equals(ownerSubject)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview not found");
        }
        return definition;
    }
}
