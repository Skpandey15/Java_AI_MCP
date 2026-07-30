package com.onlineinterview.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.util.UUID;

@Embeddable
public class QuestionCitation {
    @Column(name = "chunk_id", nullable = false) private UUID chunkId;
    @Column(name = "document_id", nullable = false) private UUID documentId;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "chunk_index", nullable = false) private int chunkIndex;
    @Column(nullable = false, length = 2000) private String excerpt;
    @Column(nullable = false) private double score;

    protected QuestionCitation() {}

    public QuestionCitation(UUID chunkId, UUID documentId, String fileName,
            int chunkIndex, String excerpt, double score) {
        this.chunkId = chunkId;
        this.documentId = documentId;
        this.fileName = fileName;
        this.chunkIndex = chunkIndex;
        this.excerpt = excerpt.length() <= 2000 ? excerpt : excerpt.substring(0, 2000);
        this.score = score;
    }

    public UUID getChunkId() { return chunkId; }
    public UUID getDocumentId() { return documentId; }
    public String getFileName() { return fileName; }
    public int getChunkIndex() { return chunkIndex; }
    public String getExcerpt() { return excerpt; }
    public double getScore() { return score; }
}
