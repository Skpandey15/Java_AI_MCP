package com.onlineinterview.messaging.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_event")
public class OutboxEvent {
    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "TEXT") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OutboxStatus status;
    @Column(nullable = false) private int attempts;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "last_error", length = 500) private String lastError;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected OutboxEvent() {}

    public static OutboxEvent pending(String aggregateType, UUID aggregateId,
            String eventType, String payload, Instant now) {
        var value = new OutboxEvent();
        value.id = UUID.randomUUID();
        value.aggregateType = aggregateType;
        value.aggregateId = aggregateId;
        value.eventType = eventType;
        value.payload = payload;
        value.status = OutboxStatus.PENDING;
        value.nextAttemptAt = now;
        value.createdAt = now;
        return value;
    }

    public void published(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    public void failed(String error, Instant now, int maximumAttempts) {
        attempts++;
        lastError = error == null ? "Unknown publishing failure"
                : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maximumAttempts) {
            status = OutboxStatus.DEAD_LETTER;
        } else {
            long delay = Math.min(300, 1L << Math.min(attempts, 8));
            nextAttemptAt = now.plusSeconds(delay);
        }
    }

    public UUID getId() { return id; }
    public String getAggregateType() { return aggregateType; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public OutboxStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getLastError() { return lastError; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getPublishedAt() { return publishedAt; }
}
