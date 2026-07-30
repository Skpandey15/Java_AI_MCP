package com.onlineinterview.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class RagQualityMetricsTest {
    @Test
    void recordsRetrievalGenerationCitationAndEvaluationMetrics() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RagQualityMetrics(registry);
        var sample = metrics.startRetrieval();

        metrics.retrievalCompleted(sample, List.of(0.7, 0.9));
        metrics.generationCompleted(2, 3, List.of(0.7, 0.9, 0.8));
        metrics.generationRejected("missing_citation");
        metrics.evaluationCompleted(
                new RetrievalQualityEvaluator.EvaluationSummary(0.8, 0.7, 0.9, 2),
                Duration.ofMillis(12));

        assertThat(registry.timer("rag.retrieval.duration", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(registry.summary("rag.retrieval.hits").totalAmount()).isEqualTo(2);
        assertThat(registry.summary("rag.retrieval.similarity").count()).isEqualTo(2);
        assertThat(registry.counter("rag.generation.total", "outcome", "success").count())
                .isEqualTo(1);
        assertThat(registry.counter("rag.generation.total", "outcome", "missing_citation").count())
                .isEqualTo(1);
        assertThat(registry.summary("rag.citations.per.question").totalAmount()).isEqualTo(1.5);
        assertThat(registry.summary("rag.citation.similarity").count()).isEqualTo(3);
        assertThat(registry.timer("rag.evaluation.duration").count()).isEqualTo(1);
        assertThat(registry.summary("rag.evaluation.precision").totalAmount()).isEqualTo(0.8);
        assertThat(registry.summary("rag.evaluation.recall").totalAmount()).isEqualTo(0.7);
        assertThat(registry.summary("rag.evaluation.mrr").totalAmount()).isEqualTo(0.9);
    }

    @Test
    void recordsNoHitsAndZeroQuestionCitationRatio() {
        var registry = new SimpleMeterRegistry();
        var metrics = new RagQualityMetrics(registry);

        metrics.retrievalCompleted(metrics.startRetrieval(), List.of());
        metrics.generationCompleted(0, 0, List.of());

        assertThat(registry.timer("rag.retrieval.duration", "outcome", "no_hits").count())
                .isEqualTo(1);
        assertThat(registry.summary("rag.citations.per.question").totalAmount()).isZero();
    }
}
