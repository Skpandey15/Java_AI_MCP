package com.onlineinterview.knowledge.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "collection_id") private KnowledgeCollection collection;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "media_type", nullable = false) private String mediaType;
    @Column(columnDefinition = "TEXT") private String content;
    @Column(name = "object_key", unique = true) private String objectKey;
    @Column(name = "object_size") private Long objectSize;
    @Column(name = "content_sha256", length = 64) private String contentSha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private DocumentStatus status;
    @Column(name = "failure_reason") private String failureReason;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected KnowledgeDocument() {}

    public static KnowledgeDocument pending(
            KnowledgeCollection collection, String fileName, String mediaType, String content) {
        return pending(collection, fileName, mediaType, content, null, null, null);
    }

    public static KnowledgeDocument pendingStored(
            KnowledgeCollection collection, String fileName, String mediaType,
            String objectKey, long objectSize, String contentSha256) {
        return pending(collection, fileName, mediaType, null,
                objectKey, objectSize, contentSha256);
    }

    private static KnowledgeDocument pending(
            KnowledgeCollection collection, String fileName, String mediaType, String content,
            String objectKey, Long objectSize, String contentSha256) {
        var document = new KnowledgeDocument();
        document.id = UUID.randomUUID();
        document.collection = collection;
        document.fileName = fileName.trim();
        document.mediaType = mediaType;
        document.content = content;
        document.objectKey = objectKey;
        document.objectSize = objectSize;
        document.contentSha256 = contentSha256;
        document.status = DocumentStatus.PENDING;
        document.createdAt = Instant.now();
        document.updatedAt = document.createdAt;
        return document;
    }

    public void startProcessing() {
        if (status != DocumentStatus.PENDING && status != DocumentStatus.FAILED) {
            throw new IllegalStateException("Only pending or failed documents can be processed");
        }
        status = DocumentStatus.PROCESSING;
        failureReason = null;
        updatedAt = Instant.now();
    }

    public void markReady() {
        if (status != DocumentStatus.PROCESSING) {
            throw new IllegalStateException("Only processing documents can become ready");
        }
        status = DocumentStatus.READY;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public KnowledgeCollection getCollection() { return collection; }
    public String getFileName() { return fileName; }
    public String getMediaType() { return mediaType; }
    public String getContent() { return content; }
    public String getObjectKey() { return objectKey; }
    public Long getObjectSize() { return objectSize; }
    public String getContentSha256() { return contentSha256; }
    public DocumentStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
