package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore.SearchHit;
import java.util.List;
import java.util.UUID;

public record KnowledgeSearchResponse(List<Citation> citations) {
    public static KnowledgeSearchResponse from(List<SearchHit> hits) {
        return new KnowledgeSearchResponse(hits.stream().map(Citation::from).toList());
    }

    public record Citation(UUID chunkId, UUID documentId, String fileName,
            int chunkIndex, String content, double score) {
        static Citation from(SearchHit hit) {
            return new Citation(hit.chunkId(), hit.documentId(), hit.fileName(),
                    hit.chunkIndex(), hit.content(), hit.score());
        }
    }
}
