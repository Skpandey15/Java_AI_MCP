package com.onlineinterview.common.resilience;

import static org.assertj.core.api.Assertions.*;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DownstreamCallExecutorTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void retriesTransientFailureAndResetsCircuitOnSuccess() {
        var properties = properties(3, 2);
        var meters = new SimpleMeterRegistry();
        var sleeps = new AtomicInteger();
        var executor = new DownstreamCallExecutor(properties, meters,
                Clock.fixed(NOW, ZoneOffset.UTC), millis -> sleeps.incrementAndGet());
        var calls = new AtomicInteger();

        assertThat(executor.execute("ai", () -> {
            if (calls.incrementAndGet() < 3) throw new IllegalStateException("temporary");
            return "ok";
        })).isEqualTo("ok");
        assertThat(calls).hasValue(3);
        assertThat(sleeps).hasValue(2);
        assertThat(meters.get("resilience.retries").counter().count()).isEqualTo(2);
        assertThat(meters.get("resilience.calls").tag("outcome", "success")
                .counter().count()).isEqualTo(1);
    }

    @Test
    void opensCircuitAfterBoundedFailuresAndRecoversAfterWindow() {
        var properties = properties(1, 2);
        var meters = new SimpleMeterRegistry();
        var clock = new MutableClock(NOW);
        var executor = new DownstreamCallExecutor(properties, meters, clock, ignored -> {});

        for (int count = 0; count < 2; count++) {
            assertThatThrownBy(() -> executor.execute(
                    "ai", () -> { throw new IllegalStateException("down"); }))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> executor.execute("ai", () -> "not called"))
                .isInstanceOf(DownstreamCallExecutor.DownstreamUnavailableException.class)
                .hasMessageContaining("open");
        clock.now = NOW.plusSeconds(31);
        assertThat(executor.execute("ai", () -> "recovered")).isEqualTo("recovered");
    }

    @Test
    void preservesInterruptAndExposesConfiguration() {
        var properties = properties(2, 5);
        properties.setRetryDelayMillis(10);
        properties.setCircuitOpenSeconds(9);
        assertThat(properties.getMaxAttempts()).isEqualTo(2);
        assertThat(properties.getRetryDelayMillis()).isEqualTo(10);
        assertThat(properties.getCircuitFailureThreshold()).isEqualTo(5);
        assertThat(properties.getCircuitOpenSeconds()).isEqualTo(9);
        var executor = new DownstreamCallExecutor(properties, new SimpleMeterRegistry(),
                Clock.fixed(NOW, ZoneOffset.UTC), ignored -> {
                    throw new InterruptedException();
                });
        assertThatThrownBy(() -> executor.execute(
                "ai", () -> { throw new IllegalStateException("down"); }))
                .isInstanceOf(DownstreamCallExecutor.DownstreamUnavailableException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.interrupted()).isTrue();
    }

    private static DownstreamResilienceProperties properties(int attempts, int threshold) {
        var value = new DownstreamResilienceProperties();
        value.setMaxAttempts(attempts);
        value.setCircuitFailureThreshold(threshold);
        value.setRetryDelayMillis(10);
        value.setCircuitOpenSeconds(30);
        return value;
    }

    private static final class MutableClock extends Clock {
        private Instant now;
        private MutableClock(Instant now) { this.now = now; }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
