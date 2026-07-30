package com.onlineinterview.messaging.application;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.messaging.domain.OutboxEvent;
import com.onlineinterview.messaging.infrastructure.OutboxEventRepository;
import java.time.*;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OutboxServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void serializesAndPersistsDomainEvent() {
        var repository = mock(OutboxEventRepository.class);
        var service = new OutboxService(repository, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        var aggregateId = UUID.randomUUID();
        service.record("INTERVIEW", aggregateId, "interview.published", Map.of("id", "one"));

        var saved = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getAggregateId()).isEqualTo(aggregateId);
        assertThat(saved.getValue().getPayload()).isEqualTo("{\"id\":\"one\"}");
    }

    @Test
    void rejectsPayloadThatCannotBeSerialized() throws Exception {
        var mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("bad") {});
        var service = new OutboxService(
                mock(OutboxEventRepository.class), mapper, Clock.systemUTC());
        assertThatThrownBy(() -> service.record(
                "X", UUID.randomUUID(), "event", Map.of("x", "y")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
