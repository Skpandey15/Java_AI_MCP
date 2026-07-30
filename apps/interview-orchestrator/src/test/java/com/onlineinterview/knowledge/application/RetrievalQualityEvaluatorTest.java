package com.onlineinterview.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore.SearchHit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalQualityEvaluatorTest {
    private final RetrievalQualityEvaluator evaluator = new RetrievalQualityEvaluator();

    @Test
    void calculatesPrecisionRecallAndReciprocalRank() {
        var relevant = UUID.randomUUID();
        var otherRelevant = UUID.randomUUID();
        var hits = List.of(hit(UUID.randomUUID()), hit(relevant), hit(otherRelevant));

        var metrics = evaluator.evaluate(hits, Set.of(relevant, otherRelevant));
        var summary = evaluator.summarize(List.of(metrics,
                new RetrievalQualityEvaluator.CaseMetrics(0, 0, 0, 0, 0)));

        assertThat(metrics.precisionAtK()).isEqualTo(2.0 / 3);
        assertThat(metrics.recallAtK()).isEqualTo(1);
        assertThat(metrics.reciprocalRank()).isEqualTo(0.5);
        assertThat(metrics.relevantHits()).isEqualTo(2);
        assertThat(metrics.retrievedHits()).isEqualTo(3);
        assertThat(summary.meanPrecisionAtK()).isEqualTo(1.0 / 3);
        assertThat(summary.meanRecallAtK()).isEqualTo(0.5);
        assertThat(summary.meanReciprocalRank()).isEqualTo(0.25);
        assertThat(summary.caseCount()).isEqualTo(2);
    }

    @Test
    void handlesNoHitsAndRejectsEmptyInputs() {
        var expected = Set.of(UUID.randomUUID());
        var metrics = evaluator.evaluate(List.of(), expected);

        assertThat(metrics.precisionAtK()).isZero();
        assertThat(metrics.recallAtK()).isZero();
        assertThat(metrics.reciprocalRank()).isZero();
        assertThatThrownBy(() -> evaluator.evaluate(List.of(), Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.summarize(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SearchHit hit(UUID id) {
        return new SearchHit(id, UUID.randomUUID(), "source.md", 0, "content", 0.8);
    }
}
