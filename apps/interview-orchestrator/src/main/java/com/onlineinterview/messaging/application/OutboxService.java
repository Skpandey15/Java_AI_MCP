package com.onlineinterview.messaging.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.onlineinterview.messaging.domain.OutboxEvent;
import com.onlineinterview.messaging.infrastructure.OutboxEventRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class OutboxService {
    private final OutboxEventRepository events;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Autowired
    public OutboxService(OutboxEventRepository events, ObjectMapper mapper) {
        this(events, mapper, Clock.systemUTC());
    }

    OutboxService(OutboxEventRepository events, ObjectMapper mapper, Clock clock) {
        this.events = events;
        this.mapper = mapper;
        this.clock = clock;
    }

    public void record(String aggregateType, UUID aggregateId,
            String eventType, Map<String, Object> payload) {
        try {
            events.save(OutboxEvent.pending(aggregateType, aggregateId, eventType,
                    mapper.writeValueAsString(payload), clock.instant()));
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Unable to serialize outbox event", exception);
        }
    }
}
