package com.onlineinterview.messaging.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.util.List;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.junit.jupiter.api.Test;

class KafkaHealthIndicatorTest {
    @Test
    void reportsBrokerAvailability() {
        var admin = mock(Admin.class);
        var result = mock(DescribeClusterResult.class);
        when(admin.describeCluster()).thenReturn(result);
        when(result.nodes()).thenReturn(KafkaFuture.completedFuture(
                List.of(new Node(1, "kafka", 9092))));
        var indicator = new KafkaHealthIndicator(() -> admin);

        assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        verify(admin).close();
    }

    @Test
    void reportsEmptyAndUnavailableClustersAsDown() {
        var emptyAdmin = mock(Admin.class);
        var emptyResult = mock(DescribeClusterResult.class);
        when(emptyAdmin.describeCluster()).thenReturn(emptyResult);
        when(emptyResult.nodes()).thenReturn(KafkaFuture.completedFuture(List.of()));
        assertThat(new KafkaHealthIndicator(() -> emptyAdmin)
                .health().getStatus().getCode()).isEqualTo("DOWN");

        var failedAdmin = mock(Admin.class);
        when(failedAdmin.describeCluster()).thenThrow(new IllegalStateException("down"));
        assertThat(new KafkaHealthIndicator(() -> failedAdmin)
                .health().getStatus().getCode()).isEqualTo("DOWN");
    }
}
