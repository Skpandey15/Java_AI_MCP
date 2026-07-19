package com.onlineinterview.session.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManualQuestionTypeTest {
    private final InterviewDefinition interview = InterviewDefinition.draft(
            "owner", "Typed interview", "Description", List.of("Java"),
            InterviewDifficulty.MEDIUM, QuestionMode.MANUAL, 30, 2);

    @Test
    void singleChoiceRequiresExactlyOneConfiguredCorrectAnswer() {
        assertThatCode(() -> ManualQuestion.create(interview, 1, "Choose one", 10,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A")))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> ManualQuestion.create(interview, 1, "Choose one", 10,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A", "B")))
                .hasMessageContaining("exactly one");
    }

    @Test
    void candidateCannotSaveUnknownMultipleChoiceOption() {
        var question = ManualQuestion.create(interview, 1, "Choose all", 10,
                QuestionType.MCQ_MULTIPLE, List.of("A", "B", "C"), List.of("A", "B"));

        assertThatCode(() -> question.validateAnswer("A\nB")).doesNotThrowAnyException();
        assertThatThrownBy(() -> question.validateAnswer("A\nUnknown"))
                .hasMessageContaining("configured options");
    }
}
