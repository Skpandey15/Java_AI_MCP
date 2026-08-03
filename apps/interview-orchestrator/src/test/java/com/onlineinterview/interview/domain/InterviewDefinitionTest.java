package com.onlineinterview.interview.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InterviewDefinitionTest {
    private InterviewDefinition draft() {
        return InterviewDefinition.draft("owner", "Java", "A Java interview",
                List.of("Java", "Spring"), InterviewDifficulty.MEDIUM, QuestionMode.MANUAL, 45, 3);
    }

    @Test
    void draftExposesConfiguredValues() {
        var definition = draft();
        assertThat(definition.getId()).isNotNull();
        assertThat(definition.getOwnerSubject()).isEqualTo("owner");
        assertThat(definition.getTitle()).isEqualTo("Java");
        assertThat(definition.getDescription()).isEqualTo("A Java interview");
        assertThat(definition.getSkills()).containsExactly("Java", "Spring");
        assertThat(definition.getDifficulty()).isEqualTo(InterviewDifficulty.MEDIUM);
        assertThat(definition.getQuestionMode()).isEqualTo(QuestionMode.MANUAL);
        assertThat(definition.getDurationMinutes()).isEqualTo(45);
        assertThat(definition.getQuestionCount()).isEqualTo(3);
        assertThat(definition.getPassingPercentage()).isEqualTo(70);
        assertThat(definition.getKnowledgeCollectionId()).isNull();
        assertThat(definition.getStatus()).isEqualTo(InterviewStatus.DRAFT);
        assertThat(definition.getCreatedAt()).isNotNull();
        assertThat(definition.getQuestionComposition().longText()).isEqualTo(3);
    }

    @Test
    void ragDraftRequiresAKnowledgeCollection() {
        var collection = UUID.randomUUID();
        var definition = InterviewDefinition.draft("owner", "RAG", "desc", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.RAG, 60, 2, 60,
                new QuestionComposition(0, 0, 0, 2), collection);
        assertThat(definition.getKnowledgeCollectionId()).isEqualTo(collection);
        assertThat(definition.getPassingPercentage()).isEqualTo(60);
    }

    @Test
    void rejectsInvalidPassingPercentage() {
        assertThatThrownBy(() -> InterviewDefinition.draft("o", "t", "d", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsCompositionTotalMismatch() {
        assertThatThrownBy(() -> InterviewDefinition.draft("o", "t", "d", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 3, 70,
                new QuestionComposition(1, 0, 0, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsKnowledgeCollectionOutsideRag() {
        assertThatThrownBy(() -> InterviewDefinition.draft("o", "t", "d", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.MANUAL, 60, 1, 70,
                QuestionComposition.allLongText(1), UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void publishesOnlyFromDraftThenArchives() {
        var definition = draft();
        definition.publish();
        assertThat(definition.getStatus()).isEqualTo(InterviewStatus.PUBLISHED);
        assertThatThrownBy(definition::publish).isInstanceOf(IllegalStateException.class);
        definition.archive();
        assertThat(definition.getStatus()).isEqualTo(InterviewStatus.ARCHIVED);
    }
}
