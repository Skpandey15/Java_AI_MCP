package com.onlineinterview.messaging.domain;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboxEventTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void transitionsThroughRetryPublicationAndDeadLetterStates() {
        var event = OutboxEvent.pending(
                "INTERVIEW", UUID.randomUUID(), "interview.published", "{}", NOW);
        assertThat(event.getId()).isNotNull();
        assertThat(event.getAggregateType()).isEqualTo("INTERVIEW");
        assertThat(event.getAggregateId()).isNotNull();
        assertThat(event.getEventType()).isEqualTo("interview.published");
        assertThat(event.getPayload()).isEqualTo("{}");
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(event.getCreatedAt()).isEqualTo(NOW);
        assertThat(event.getPublishedAt()).isNull();

        event.failed("x".repeat(600), NOW, 3);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).hasSize(500);
        assertThat(event.getNextAttemptAt()).isAfter(NOW);
        event.published(NOW.plusSeconds(3));
        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getPublishedAt()).isEqualTo(NOW.plusSeconds(3));
        assertThat(event.getLastError()).isNull();

        var dead = OutboxEvent.pending(
                "SESSION", UUID.randomUUID(), "review.finalized", "{}", NOW);
        dead.failed(null, NOW, 1);
        assertThat(dead.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(dead.getLastError()).isEqualTo("Unknown publishing failure");
    }
}
