package com.onlineinterview.session.api;

import com.onlineinterview.session.domain.QuestionCitation;
import java.util.UUID;

public record QuestionCitationResponse(
        UUID chunkId, UUID documentId, String fileName, int chunkIndex,
        String excerpt, double score) {
    public static QuestionCitationResponse from(QuestionCitation citation) {
        return new QuestionCitationResponse(citation.getChunkId(), citation.getDocumentId(),
                citation.getFileName(), citation.getChunkIndex(), citation.getExcerpt(),
                citation.getScore());
    }
}
