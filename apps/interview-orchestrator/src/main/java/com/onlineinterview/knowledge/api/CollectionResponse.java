package com.onlineinterview.knowledge.api;

import com.onlineinterview.knowledge.domain.KnowledgeCollection;
import java.time.Instant;
import java.util.UUID;

public record CollectionResponse(
        UUID id, String name, String description, Instant createdAt) {
    public static CollectionResponse from(KnowledgeCollection value) {
        return new CollectionResponse(
                value.getId(), value.getName(), value.getDescription(), value.getCreatedAt());
    }
}
