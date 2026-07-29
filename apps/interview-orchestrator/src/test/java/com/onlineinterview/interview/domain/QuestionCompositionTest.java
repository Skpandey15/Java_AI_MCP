package com.onlineinterview.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.session.domain.QuestionType;
import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionCompositionTest {
    @Test
    void storesTheRequestedMixedComposition() {
        var composition = new QuestionComposition(2, 1, 3, 4);
        var definition = InterviewDefinition.draft(
                "owner", "Mixed interview", "Description", List.of("Java"),
                InterviewDifficulty.MEDIUM, QuestionMode.DIRECT_LLM,
                60, 10, 70, composition);

        assertThat(definition.getQuestionComposition()).isEqualTo(composition);
        assertThat(composition.count(QuestionType.MCQ_SINGLE)).isEqualTo(2);
        assertThat(composition.total()).isEqualTo(10);
    }

    @Test
    void rejectsACompositionThatDoesNotMatchTheTotal() {
        assertThatThrownBy(() -> InterviewDefinition.draft(
                "owner", "Invalid interview", "Description", List.of("Java"),
                InterviewDifficulty.MEDIUM, QuestionMode.MANUAL,
                60, 5, 70, new QuestionComposition(1, 1, 1, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("composition total");
    }
}
