package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.application.RetrievalQualityEvaluator.EvaluationSummary;

public record RetrievalEvaluationResponse(
        double meanPrecisionAtK, double meanRecallAtK, double meanReciprocalRank,
        int caseCount, int retrievalLimit, double minimumSimilarity) {
    static RetrievalEvaluationResponse from(
            EvaluationSummary summary, int limit, double threshold) {
        return new RetrievalEvaluationResponse(summary.meanPrecisionAtK(),
                summary.meanRecallAtK(), summary.meanReciprocalRank(), summary.caseCount(),
                limit, threshold);
    }
}
