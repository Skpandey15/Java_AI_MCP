package com.onlineinterview.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewDifficulty;
import com.onlineinterview.interview.domain.QuestionMode;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ManualQuestionValidationTest {
    private InterviewDefinition definition() {
        return InterviewDefinition.draft("owner", "Java", "desc", List.of("Java"),
                InterviewDifficulty.HARD, QuestionMode.DIRECT_LLM, 60, 1);
    }

    @Test
    void createsAndExposesMcqSingle() {
        var q = ManualQuestion.create(definition(), 1, "Pick one", 10,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A"));
        assertThat(q.getId()).isNotNull();
        assertThat(q.getOrder()).isEqualTo(1);
        assertThat(q.getPrompt()).isEqualTo("Pick one");
        assertThat(q.getMaxScore()).isEqualTo(10);
        assertThat(q.getType()).isEqualTo(QuestionType.MCQ_SINGLE);
        assertThat(q.getOptions()).containsExactly("A", "B");
        assertThat(q.getCorrectAnswers()).containsExactly("A");
        assertThat(q.getSource()).isEqualTo(QuestionSource.MANUAL);
        assertThat(q.getInterviewDefinition()).isNotNull();
        assertThat(q.getCitations()).isEmpty();
        assertThat(q.getGenerationRequestId()).isNull();
        assertThat(q.getModelPolicy()).isNull();
        assertThat(q.getPromptVersion()).isNull();
    }

    @Test
    void validationRejectsBadMcqAndTextConfig() {
        var def = definition();
        assertThatThrownBy(() -> ManualQuestion.create(def, 1, "q", 5,
                QuestionType.MCQ_SINGLE, List.of("A"), List.of("A")))
                .isInstanceOf(IllegalArgumentException.class); // too few options
        assertThatThrownBy(() -> ManualQuestion.create(def, 1, "q", 5,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("C")))
                .isInstanceOf(IllegalArgumentException.class); // correct not an option
        assertThatThrownBy(() -> ManualQuestion.create(def, 1, "q", 5,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A", "B")))
                .isInstanceOf(IllegalArgumentException.class); // single needs exactly one
        assertThatThrownBy(() -> ManualQuestion.create(def, 1, "q", 5,
                QuestionType.LONG_TEXT, List.of("A"), List.of()))
                .isInstanceOf(IllegalArgumentException.class); // text cannot have options
    }

    @Test
    void validateAnswerEnforcesTypeRules() {
        var single = ManualQuestion.create(definition(), 1, "q", 5,
                QuestionType.MCQ_SINGLE, List.of("A", "B"), List.of("A"));
        assertThatCode(() -> single.validateAnswer("A")).doesNotThrowAnyException();
        assertThatThrownBy(() -> single.validateAnswer("Z"))
                .isInstanceOf(IllegalArgumentException.class);

        var multi = ManualQuestion.create(definition(), 1, "q", 5,
                QuestionType.MCQ_MULTIPLE, List.of("A", "B", "C"), List.of("A", "B"));
        assertThatCode(() -> multi.validateAnswer("A\nB")).doesNotThrowAnyException();
        assertThatThrownBy(() -> multi.validateAnswer("")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> multi.validateAnswer("Z"))
                .isInstanceOf(IllegalArgumentException.class);

        var shortText = ManualQuestion.create(definition(), 1, "q", 5,
                QuestionType.SHORT_TEXT, List.of(), List.of());
        assertThatCode(() -> shortText.validateAnswer("ok")).doesNotThrowAnyException();
        assertThatThrownBy(() -> shortText.validateAnswer("x".repeat(1001)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateRevalidates() {
        var q = ManualQuestion.create(definition(), 1, "q", 5,
                QuestionType.LONG_TEXT, List.of(), List.of());
        q.update(2, "changed", 8, QuestionType.SHORT_TEXT, List.of(), List.of());
        assertThat(q.getOrder()).isEqualTo(2);
        assertThat(q.getMaxScore()).isEqualTo(8);
        assertThat(q.getType()).isEqualTo(QuestionType.SHORT_TEXT);
    }

    @Test
    void generatedAndRagFactoriesSetSourceAndCitations() {
        var requestId = UUID.randomUUID();
        var direct = ManualQuestion.generated(definition(), 1, "q", 10,
                QuestionType.LONG_TEXT, List.of(), List.of(), requestId, "policy", "v1");
        assertThat(direct.getSource()).isEqualTo(QuestionSource.AI_DIRECT);
        assertThat(direct.getGenerationRequestId()).isEqualTo(requestId);
        assertThat(direct.getModelPolicy()).isEqualTo("policy");
        assertThat(direct.getPromptVersion()).isEqualTo("v1");

        var citation = new QuestionCitation(UUID.randomUUID(), UUID.randomUUID(),
                "doc.md", 2, "excerpt", 0.9);
        var rag = ManualQuestion.generatedRag(definition(), 1, "q", 10,
                QuestionType.LONG_TEXT, List.of(), List.of(), requestId, "policy", "v1",
                List.of(citation));
        assertThat(rag.getSource()).isEqualTo(QuestionSource.AI_RAG);
        assertThat(rag.getCitations()).hasSize(1);

        assertThatThrownBy(() -> ManualQuestion.generatedRag(definition(), 1, "q", 10,
                QuestionType.LONG_TEXT, List.of(), List.of(), requestId, "policy", "v1", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void questionCitationExposesFieldsAndTruncatesExcerpt() {
        var chunk = UUID.randomUUID();
        var doc = UUID.randomUUID();
        var citation = new QuestionCitation(chunk, doc, "f.md", 3, "hello", 0.75);
        assertThat(citation.getChunkId()).isEqualTo(chunk);
        assertThat(citation.getDocumentId()).isEqualTo(doc);
        assertThat(citation.getFileName()).isEqualTo("f.md");
        assertThat(citation.getChunkIndex()).isEqualTo(3);
        assertThat(citation.getExcerpt()).isEqualTo("hello");
        assertThat(citation.getScore()).isEqualTo(0.75);

        var truncated = new QuestionCitation(chunk, doc, "f.md", 0, "x".repeat(2500), 0.1);
        assertThat(truncated.getExcerpt()).hasSize(2000);
    }
}
