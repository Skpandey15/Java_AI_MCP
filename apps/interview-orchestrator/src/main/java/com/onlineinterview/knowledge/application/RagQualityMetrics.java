package com.onlineinterview.knowledge.application;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RagQualityMetrics {
    private final MeterRegistry registry;

    public RagQualityMetrics(MeterRegistry registry) { this.registry = registry; }

    public Timer.Sample startRetrieval() { return Timer.start(registry); }

    public void retrievalCompleted(Timer.Sample sample, List<Double> scores) {
        sample.stop(registry.timer("rag.retrieval.duration", "outcome",
                scores.isEmpty() ? "no_hits" : "success"));
        registry.summary("rag.retrieval.hits").record(scores.size());
        scores.forEach(score -> registry.summary("rag.retrieval.similarity").record(score));
    }

    public void generationCompleted(int questionCount, int citationCount, List<Double> scores) {
        registry.counter("rag.generation.total", "outcome", "success").increment();
        registry.summary("rag.citations.per.question")
                .record(questionCount == 0 ? 0 : (double) citationCount / questionCount);
        scores.forEach(score -> registry.summary("rag.citation.similarity").record(score));
    }

    public void generationRejected(String reason) {
        registry.counter("rag.generation.total", "outcome", reason).increment();
    }

    public void evaluationCompleted(
            RetrievalQualityEvaluator.EvaluationSummary summary, Duration duration) {
        registry.timer("rag.evaluation.duration").record(duration);
        registry.summary("rag.evaluation.precision").record(summary.meanPrecisionAtK());
        registry.summary("rag.evaluation.recall").record(summary.meanRecallAtK());
        registry.summary("rag.evaluation.mrr").record(summary.meanReciprocalRank());
    }
}
