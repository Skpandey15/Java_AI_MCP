package com.onlineinterview.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_collection")
public class KnowledgeCollection {
    @Id private UUID id;
    @Column(name = "owner_subject", nullable = false) private String ownerSubject;
    @Column(nullable = false) private String name;
    @Column(nullable = false) private String description;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Version private long version;

    protected KnowledgeCollection() {}

    public static KnowledgeCollection create(String ownerSubject, String name, String description) {
        var collection = new KnowledgeCollection();
        collection.id = UUID.randomUUID();
        collection.ownerSubject = ownerSubject;
        collection.name = name.trim();
        collection.description = description.trim();
        collection.createdAt = Instant.now();
        return collection;
    }

    public UUID getId() { return id; }
    public String getOwnerSubject() { return ownerSubject; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
