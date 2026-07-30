package com.onlineinterview.messaging.infrastructure;

import org.apache.kafka.common.TopicPartition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaReliabilityConfig {
    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<Object, Object> template) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(
                        record.topic() + ".DLT", record.partition()));
        var backoff = new ExponentialBackOff(1000, 2);
        backoff.setMaxInterval(30_000);
        backoff.setMaxElapsedTime(120_000);
        return new DefaultErrorHandler(recoverer, backoff);
    }
}
