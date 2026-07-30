package com.onlineinterview.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.onlineinterview.knowledge.api.EvaluateRetrievalRequest.EvaluationCase;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore.SearchHit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RetrievalEvaluationServiceTest {
    @Test
    void evaluatesOwnedCollectionUsingConfiguredRetrievalPolicy() {
        var knowledge = mock(KnowledgeService.class);
        var metrics = mock(RagQualityMetrics.class);
        var properties = new RagProperties();
        properties.setRetrievalLimit(5);
        properties.setMinimumSimilarity(0.7);
        var service = new RetrievalEvaluationService(knowledge,
                new RetrievalQualityEvaluator(), metrics, properties);
        var collectionId = UUID.randomUUID();
        var expected = UUID.randomUUID();
        var hit = new SearchHit(expected, UUID.randomUUID(), "source.md", 0, "content", 0.9);
        when(knowledge.search("owner", collectionId, "records", 5, 0.7))
                .thenReturn(List.of(hit));

        var result = service.evaluate("owner", collectionId,
                List.of(new EvaluationCase("records", Set.of(expected))));

        assertThat(result.meanPrecisionAtK()).isEqualTo(1);
        assertThat(result.meanRecallAtK()).isEqualTo(1);
        assertThat(result.meanReciprocalRank()).isEqualTo(1);
        verify(metrics).evaluationCompleted(eq(result), any());
    }
}
