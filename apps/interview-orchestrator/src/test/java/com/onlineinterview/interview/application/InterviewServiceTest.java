package com.onlineinterview.interview.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import com.onlineinterview.interview.infrastructure.InterviewAssignmentRepository;
import com.onlineinterview.interview.infrastructure.InterviewDefinitionRepository;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InterviewServiceTest {
    private final InterviewDefinitionRepository definitions = mock(InterviewDefinitionRepository.class);
    private final InterviewAssignmentRepository assignments = mock(InterviewAssignmentRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final ManualQuestionRepository questions = mock(ManualQuestionRepository.class);
    private final InterviewSessionRepository sessions = mock(InterviewSessionRepository.class);
    private final InterviewService service =
            new InterviewService(definitions, assignments, profiles, questions, sessions,
                    org.mockito.Mockito.mock(
                            com.onlineinterview.knowledge.infrastructure.KnowledgeCollectionRepository.class));

    @Test
    void listsOnlyInterviewsOwnedByAuthenticatedSubject() {
        when(definitions.findByOwnerSubjectOrderByCreatedAtDesc("owner-1")).thenReturn(List.of());
        assertThat(service.listOwned("owner-1")).isEmpty();
    }

    @Test
    void hidesAnInterviewOwnedByAnotherInterviewer() {
        UUID id = UUID.randomUUID();
        var definition = InterviewDefinition.draft("owner-2", "Java", "Senior Java interview",
                List.of("Concurrency"), InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 5);
        when(definitions.findById(id)).thenReturn(Optional.of(definition));

        assertThatThrownBy(() -> service.publish("owner-1", id))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }

    @Test
    void hidesCandidateFromAnotherTenantDuringAssignment() {
        UUID interviewId = UUID.randomUUID();
        var definition = InterviewDefinition.draft(
                "owner-1", "Java", "Java interview", List.of("Java"),
                InterviewDifficulty.MEDIUM, QuestionMode.MANUAL, 30, 1);
        var owner = UserProfile.registerCandidate(
                "tenant-a", "owner-1", "owner@example.com", "Owner");
        var candidate = UserProfile.registerCandidate(
                "tenant-b", "candidate-1", "candidate@example.com", "Candidate");
        when(definitions.findById(interviewId)).thenReturn(Optional.of(definition));
        when(profiles.findById(candidate.getId())).thenReturn(Optional.of(candidate));
        when(profiles.findByIdentitySubject("owner-1")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> service.assign(
                "owner-1", interviewId, candidate.getId(),
                java.time.Instant.now().plusSeconds(60),
                java.time.Instant.now().plusSeconds(3600), 1))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
