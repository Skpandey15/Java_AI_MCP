package com.onlineinterview.knowledge.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "knowledge_chunk")
public class KnowledgeChunk {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id") private KnowledgeDocument document;
    @Column(name = "chunk_index", nullable = false) private int index;
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(name = "token_estimate", nullable = false) private int tokenEstimate;

    protected KnowledgeChunk() {}

    public static KnowledgeChunk create(
            KnowledgeDocument document, int index, String content) {
        var chunk = new KnowledgeChunk();
        chunk.id = UUID.randomUUID();
        chunk.document = document;
        chunk.index = index;
        chunk.content = content;
        chunk.tokenEstimate = Math.max(1, (content.length() + 3) / 4);
        return chunk;
    }

    public UUID getId() { return id; }
    public KnowledgeDocument getDocument() { return document; }
    public int getIndex() { return index; }
    public String getContent() { return content; }
    public int getTokenEstimate() { return tokenEstimate; }
}
