package com.onlineinterview.messaging.application;

import com.onlineinterview.messaging.infrastructure.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaOutboxPublisher {
    private final OutboxEventRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;
    private final Clock clock;
    private final String topic;
    private final int maximumAttempts;

    @Autowired
    public KafkaOutboxPublisher(OutboxEventRepository events, KafkaTemplate<String, String> kafka,
            MeterRegistry meters, @Value("${app.messaging.topic}") String topic,
            @Value("${app.messaging.maximum-attempts:8}") int maximumAttempts) {
        this(events, kafka, meters, Clock.systemUTC(), topic, maximumAttempts);
    }

    KafkaOutboxPublisher(OutboxEventRepository events, KafkaTemplate<String, String> kafka,
            MeterRegistry meters, Clock clock, String topic, int maximumAttempts) {
        this.events = events;
        this.kafka = kafka;
        this.meters = meters;
        this.clock = clock;
        this.topic = topic;
        this.maximumAttempts = maximumAttempts;
    }

    @Scheduled(fixedDelayString = "${app.messaging.poll-interval-ms:1000}")
    @Transactional
    public void publish() {
        for (var event : events.lockPublishable(clock.instant())) {
            try {
                kafka.send(topic, event.getId().toString(), event.getPayload())
                        .get(10, TimeUnit.SECONDS);
                event.published(clock.instant());
                meters.counter("outbox.events", "outcome", "published").increment();
            } catch (Exception exception) {
                if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
                event.failed(exception.getMessage(), clock.instant(), maximumAttempts);
                meters.counter("outbox.events", "outcome",
                        event.getStatus().name().toLowerCase()).increment();
            }
        }
    }
}
