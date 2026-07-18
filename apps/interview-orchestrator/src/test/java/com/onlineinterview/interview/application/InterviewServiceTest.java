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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class InterviewServiceTest {
    private final InterviewDefinitionRepository definitions = mock(InterviewDefinitionRepository.class);
    private final InterviewAssignmentRepository assignments = mock(InterviewAssignmentRepository.class);
    private final UserProfileRepository profiles = mock(UserProfileRepository.class);
    private final InterviewService service = new InterviewService(definitions, assignments, profiles);

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
}
