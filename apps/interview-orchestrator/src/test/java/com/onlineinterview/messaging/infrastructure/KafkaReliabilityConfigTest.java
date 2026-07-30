package com.onlineinterview.messaging.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class KafkaReliabilityConfigTest {
    @Test
    @SuppressWarnings("unchecked")
    void createsBoundedConsumerErrorHandlerWithDltRecovery() {
        var handler = new KafkaReliabilityConfig().kafkaErrorHandler(mock(KafkaTemplate.class));
        assertThat(handler).isNotNull();
        assertThat(handler.isAckAfterHandle()).isTrue();
    }
}
