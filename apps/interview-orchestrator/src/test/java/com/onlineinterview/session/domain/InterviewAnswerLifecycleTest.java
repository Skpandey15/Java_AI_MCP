package com.onlineinterview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.interview.domain.InterviewAssignment;
import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewAnswerLifecycleTest {
    private final Instant now = Instant.parse("2026-07-18T10:00:00Z");

    private InterviewSession session() {
        var definition = InterviewDefinition.draft("owner", "Java", "desc", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1);
        definition.publish();
        var assignment = InterviewAssignment.schedule(definition, UUID.randomUUID(),
                now.minusSeconds(60), now.plusSeconds(7200), 1);
        return InterviewSession.start(assignment, assignment.getCandidateId(), now);
    }

    private ManualQuestion mcqSingle() {
        return ManualQuestion.create(session().getAssignment().getInterviewDefinition(), 1,
                "Pick one", 10, QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A"));
    }

    @Test
    void createExposesContentAndMetadata() {
        var question = mcqSingle();
        var answer = InterviewAnswer.create(session(), question, "A", now);
        assertThat(answer.getId()).isNotNull();
        assertThat(answer.getContent()).isEqualTo("A");
        assertThat(answer.getQuestion()).isSameAs(question);
        assertThat(answer.getQuestionId()).isEqualTo(question.getId());
        assertThat(answer.getUpdatedAt()).isEqualTo(now);
        assertThat(answer.getVersion()).isZero();
        assertThat(answer.getAwardedScore()).isNull();
        assertThat(answer.getReviewerFeedback()).isNull();
        assertThat(answer.isAutoScored()).isFalse();
    }

    @Test
    void scoresMcqSingleCorrectAndWrong() {
        var correct = InterviewAnswer.create(session(), mcqSingle(), "A", now);
        correct.scoreObjective();
        assertThat(correct.getAwardedScore()).isEqualTo(10);
        assertThat(correct.isAutoScored()).isTrue();

        var wrong = InterviewAnswer.create(session(), mcqSingle(), "B", now);
        wrong.scoreObjective();
        assertThat(wrong.getAwardedScore()).isZero();
    }

    @Test
    void scoresMcqMultipleAndIgnoresTextQuestions() {
        var definition = session().getAssignment().getInterviewDefinition();
        var multi = ManualQuestion.create(definition, 1, "Pick all", 6,
                QuestionType.MCQ_MULTIPLE, List.of("A", "B", "C"), List.of("A", "B"));
        var right = InterviewAnswer.create(session(), multi, "A\nB", now);
        right.scoreObjective();
        assertThat(right.getAwardedScore()).isEqualTo(6);

        var partial = InterviewAnswer.create(session(), multi, "A", now);
        partial.scoreObjective();
        assertThat(partial.getAwardedScore()).isZero();

        var text = ManualQuestion.create(definition, 2, "Explain", 10,
                QuestionType.LONG_TEXT, List.of(), List.of());
        var essay = InterviewAnswer.create(session(), text, "an essay", now);
        essay.scoreObjective();
        assertThat(essay.getAwardedScore()).isNull();
        assertThat(essay.isAutoScored()).isFalse();
    }

    @Test
    void reviewValidatesBounds() {
        var answer = InterviewAnswer.create(session(), mcqSingle(), "A", now);
        answer.review(7, "good");
        assertThat(answer.getAwardedScore()).isEqualTo(7);
        assertThat(answer.getReviewerFeedback()).isEqualTo("good");
        assertThat(answer.isAutoScored()).isFalse();
        assertThatThrownBy(() -> answer.review(11, "too high"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> answer.review(-1, "negative"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateEnforcesOptimisticVersion() {
        var answer = InterviewAnswer.create(session(), mcqSingle(), "A", now);
        answer.update("B", 0, now.plusSeconds(1));
        assertThat(answer.getContent()).isEqualTo("B");
        assertThatThrownBy(() -> answer.update("C", 99, now))
                .isInstanceOf(IllegalStateException.class);
    }
}
