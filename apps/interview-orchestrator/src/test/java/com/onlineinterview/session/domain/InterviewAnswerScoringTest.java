package com.onlineinterview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewAnswerScoringTest {
    private final InterviewDefinition definition = publishedDefinition();
    private final InterviewAssignment assignment = InterviewAssignment.schedule(
            definition, UUID.randomUUID(), Instant.now().minusSeconds(60),
            Instant.now().plusSeconds(3600), 1);
    private final InterviewSession session = InterviewSession.start(
            assignment, assignment.getCandidateId(), Instant.now());

    @Test
    void scoresSingleAndMultipleChoiceByExactAnswer() {
        var single = ManualQuestion.create(definition, 1, "Choose one", 10,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("B"));
        var multiple = ManualQuestion.create(definition, 2, "Choose all", 20,
                QuestionType.MCQ_MULTIPLE, List.of("A", "B", "C"), List.of("A", "C"));

        var correctSingle = InterviewAnswer.create(session, single, "B", Instant.now());
        var partialMultiple = InterviewAnswer.create(session, multiple, "A", Instant.now());
        correctSingle.scoreObjective();
        partialMultiple.scoreObjective();

        assertThat(correctSingle.getAwardedScore()).isEqualTo(10);
        assertThat(correctSingle.isAutoScored()).isTrue();
        assertThat(partialMultiple.getAwardedScore()).isZero();
    }

    private static InterviewDefinition publishedDefinition() {
        var definition = InterviewDefinition.draft("owner", "Scoring", "Description",
                List.of("Java"), InterviewDifficulty.MEDIUM, QuestionMode.MANUAL, 30, 2);
        definition.publish();
        return definition;
    }
}
