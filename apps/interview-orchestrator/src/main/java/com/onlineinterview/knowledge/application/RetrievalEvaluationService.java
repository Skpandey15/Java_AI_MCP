package com.onlineinterview.knowledge.application;

import com.onlineinterview.knowledge.api.EvaluateRetrievalRequest.EvaluationCase;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RetrievalEvaluationService {
    private final KnowledgeService knowledge;
    private final RetrievalQualityEvaluator evaluator;
    private final RagQualityMetrics metrics;
    private final RagProperties properties;

    public RetrievalEvaluationService(KnowledgeService knowledge,
            RetrievalQualityEvaluator evaluator, RagQualityMetrics metrics,
            RagProperties properties) {
        this.knowledge = knowledge;
        this.evaluator = evaluator;
        this.metrics = metrics;
        this.properties = properties;
    }

    public RetrievalQualityEvaluator.EvaluationSummary evaluate(
            String owner, UUID collectionId, List<EvaluationCase> cases) {
        var started = Instant.now();
        var results = cases.stream().map(item -> evaluator.evaluate(
                knowledge.search(owner, collectionId, item.query(),
                        properties.getRetrievalLimit(), properties.getMinimumSimilarity()),
                item.expectedChunkIds())).toList();
        var summary = evaluator.summarize(results);
        metrics.evaluationCompleted(summary, Duration.between(started, Instant.now()));
        return summary;
    }
}
