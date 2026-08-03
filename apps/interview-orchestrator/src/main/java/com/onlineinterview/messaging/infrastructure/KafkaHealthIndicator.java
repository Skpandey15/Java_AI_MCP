package com.onlineinterview.messaging.infrastructure;

import java.util.function.Supplier;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.messaging.enabled", havingValue = "true")
public class KafkaHealthIndicator implements HealthIndicator {
    private final Supplier<Admin> clients;

    @Autowired
    public KafkaHealthIndicator(KafkaAdmin admin) {
        this(() -> Admin.create(admin.getConfigurationProperties()));
    }

    KafkaHealthIndicator(Supplier<Admin> clients) {
        this.clients = clients;
    }

    @Override
    public Health health() {
        try (var client = clients.get()) {
            var nodes = client.describeCluster().nodes().get(2, TimeUnit.SECONDS);
            return nodes.isEmpty()
                    ? Health.down().withDetail("reason", "cluster has no brokers").build()
                    : Health.up().withDetail("brokers", nodes.size()).build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }
}
