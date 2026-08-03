package com.onlineinterview.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewAssignmentTest {
    private final Instant start = Instant.parse("2026-07-18T10:00:00Z");

    private InterviewDefinition published() {
        var definition = InterviewDefinition.draft("owner", "Java", "desc",
                List.of("Java"), InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1);
        definition.publish();
        return definition;
    }

    @Test
    void schedulesAgainstAPublishedInterview() {
        var candidate = UUID.randomUUID();
        var assignment = InterviewAssignment.schedule(
                published(), candidate, start, start.plusSeconds(3600), 2);

        assertThat(assignment.getId()).isNotNull();
        assertThat(assignment.getInterviewDefinition().getStatus())
                .isEqualTo(InterviewStatus.PUBLISHED);
        assertThat(assignment.getCandidateId()).isEqualTo(candidate);
        assertThat(assignment.getStartsAt()).isEqualTo(start);
        assertThat(assignment.getEndsAt()).isEqualTo(start.plusSeconds(3600));
        assertThat(assignment.getMaxAttempts()).isEqualTo(2);
        assertThat(assignment.getStatus()).isEqualTo(AssignmentStatus.SCHEDULED);
    }

    @Test
    void rejectsSchedulingADraftInterview() {
        var draft = InterviewDefinition.draft("owner", "Java", "desc",
                List.of("Java"), InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1);
        assertThatThrownBy(() -> InterviewAssignment.schedule(
                draft, UUID.randomUUID(), start, start.plusSeconds(3600), 1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsEndBeforeStart() {
        assertThatThrownBy(() -> InterviewAssignment.schedule(
                published(), UUID.randomUUID(), start, start, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
