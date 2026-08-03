package com.onlineinterview.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.session.domain.QuestionType;
import org.junit.jupiter.api.Test;

class QuestionCompositionCoverageTest {
    @Test
    void countsTotalsAndMapsEachType() {
        var composition = new QuestionComposition(1, 2, 3, 4);
        assertThat(composition.total()).isEqualTo(10);
        assertThat(composition.count(QuestionType.MCQ_SINGLE)).isEqualTo(1);
        assertThat(composition.count(QuestionType.MCQ_MULTIPLE)).isEqualTo(2);
        assertThat(composition.count(QuestionType.SHORT_TEXT)).isEqualTo(3);
        assertThat(composition.count(QuestionType.LONG_TEXT)).isEqualTo(4);
        assertThat(composition.asMap())
                .containsEntry(QuestionType.MCQ_SINGLE, 1)
                .containsEntry(QuestionType.LONG_TEXT, 4);
    }

    @Test
    void allLongTextBuildsLongOnlyComposition() {
        var composition = QuestionComposition.allLongText(5);
        assertThat(composition.longText()).isEqualTo(5);
        assertThat(composition.mcqSingle()).isZero();
        assertThat(composition.total()).isEqualTo(5);
    }

    @Test
    void rejectsNegativeCounts() {
        assertThatThrownBy(() -> new QuestionComposition(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
