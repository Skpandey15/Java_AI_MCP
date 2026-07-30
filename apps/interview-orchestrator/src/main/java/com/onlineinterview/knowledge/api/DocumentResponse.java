package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.domain.DocumentStatus;
import com.onlineinterview.knowledge.domain.KnowledgeDocument;
import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id, UUID collectionId, String fileName, String mediaType,
        DocumentStatus status, String failureReason, Instant createdAt, Instant updatedAt) {
    public static DocumentResponse from(KnowledgeDocument value) {
        return new DocumentResponse(value.getId(), value.getCollection().getId(),
                value.getFileName(), value.getMediaType(), value.getStatus(),
                value.getFailureReason(), value.getCreatedAt(), value.getUpdatedAt());
    }
}
