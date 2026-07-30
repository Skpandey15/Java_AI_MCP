package com.onlineinterview.messaging.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.onlineinterview.messaging.domain.*;
import com.onlineinterview.messaging.infrastructure.OutboxEventRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaOutboxPublisherTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void publishesLockedEventsWithStableEventIdKey() {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var event = event();
        when(repository.lockPublishable(NOW)).thenReturn(List.of(event));
        when(kafka.send("events", event.getId().toString(), event.getPayload()))
                .thenReturn(CompletableFuture.completedFuture(null));
        var meters = new SimpleMeterRegistry();
        var publisher = new KafkaOutboxPublisher(repository, kafka, meters,
                Clock.fixed(NOW, ZoneOffset.UTC), "events", 3);

        publisher.publish();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(meters.get("outbox.events").tag("outcome", "published")
                .counter().count()).isEqualTo(1);
    }

    @Test
    @SuppressWarnings("unchecked")
    void retainsFailedEventForBoundedRetry() {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var event = event();
        when(repository.lockPublishable(NOW)).thenReturn(List.of(event));
        var failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);
        var publisher = new KafkaOutboxPublisher(repository, kafka,
                new SimpleMeterRegistry(), Clock.fixed(NOW, ZoneOffset.UTC), "events", 3);

        publisher.publish();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("broker down");
    }

    @Test
    @SuppressWarnings("unchecked")
    void deadLettersExhaustedEvent() {
        var repository = mock(OutboxEventRepository.class);
        var kafka = mock(KafkaTemplate.class);
        var event = event();
        when(repository.lockPublishable(NOW)).thenReturn(List.of(event));
        var failed = new CompletableFuture<>();
        failed.completeExceptionally(new IllegalStateException("broker down"));
        when(kafka.send(anyString(), anyString(), anyString())).thenReturn(failed);
        var meters = new SimpleMeterRegistry();
        var publisher = new KafkaOutboxPublisher(repository, kafka, meters,
                Clock.fixed(NOW, ZoneOffset.UTC), "events", 1);

        publisher.publish();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.DEAD_LETTER);
        assertThat(meters.get("outbox.events").tag("outcome", "dead_letter")
                .counter().count()).isEqualTo(1);
    }

    private static OutboxEvent event() {
        return OutboxEvent.pending(
                "INTERVIEW", UUID.randomUUID(), "interview.published", "{}", NOW);
    }
}
