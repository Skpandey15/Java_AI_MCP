package com.onlineinterview.knowledge.application;

import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore.SearchHit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RetrievalQualityEvaluator {
    public CaseMetrics evaluate(List<SearchHit> hits, Set<UUID> expectedChunkIds) {
        if (expectedChunkIds.isEmpty()) {
            throw new IllegalArgumentException("Expected chunk IDs cannot be empty");
        }
        var uniqueExpected = Set.copyOf(expectedChunkIds);
        var seen = new HashSet<UUID>();
        int relevant = 0;
        double reciprocalRank = 0;
        for (int index = 0; index < hits.size(); index++) {
            UUID id = hits.get(index).chunkId();
            if (seen.add(id) && uniqueExpected.contains(id)) {
                relevant++;
                if (reciprocalRank == 0) reciprocalRank = 1.0 / (index + 1);
            }
        }
        double precision = hits.isEmpty() ? 0 : (double) relevant / hits.size();
        double recall = (double) relevant / uniqueExpected.size();
        return new CaseMetrics(precision, recall, reciprocalRank, relevant, hits.size());
    }

    public EvaluationSummary summarize(List<CaseMetrics> cases) {
        if (cases.isEmpty()) throw new IllegalArgumentException("Evaluation cases cannot be empty");
        return new EvaluationSummary(
                cases.stream().mapToDouble(CaseMetrics::precisionAtK).average().orElse(0),
                cases.stream().mapToDouble(CaseMetrics::recallAtK).average().orElse(0),
                cases.stream().mapToDouble(CaseMetrics::reciprocalRank).average().orElse(0),
                cases.size());
    }

    public record CaseMetrics(double precisionAtK, double recallAtK, double reciprocalRank,
            int relevantHits, int retrievedHits) {}
    public record EvaluationSummary(
            double meanPrecisionAtK, double meanRecallAtK, double meanReciprocalRank,
            int caseCount) {}
}
