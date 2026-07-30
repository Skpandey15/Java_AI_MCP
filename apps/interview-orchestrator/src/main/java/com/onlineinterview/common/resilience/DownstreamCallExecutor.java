package com.onlineinterview.common.resilience;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class DownstreamCallExecutor {
    private final DownstreamResilienceProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Sleeper sleeper;
    private final ConcurrentHashMap<String, CircuitState> circuits = new ConcurrentHashMap<>();

    @Autowired
    public DownstreamCallExecutor(
            DownstreamResilienceProperties properties, MeterRegistry meters) {
        this(properties, meters, Clock.systemUTC(), Thread::sleep);
    }

    DownstreamCallExecutor(DownstreamResilienceProperties properties, MeterRegistry meters,
            Clock clock, Sleeper sleeper) {
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
        this.sleeper = sleeper;
    }

    public <T> T execute(String dependency, Supplier<T> operation) {
        var state = circuits.computeIfAbsent(dependency, ignored -> new CircuitState());
        Instant now = clock.instant();
        synchronized (state) {
            if (state.openUntil != null && state.openUntil.isAfter(now)) {
                meters.counter("resilience.calls", "dependency", dependency,
                        "outcome", "short_circuited").increment();
                throw new DownstreamUnavailableException(dependency + " circuit is open");
            }
            if (state.openUntil != null) state.reset();
        }
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                T result = operation.get();
                synchronized (state) { state.reset(); }
                meters.counter("resilience.calls", "dependency", dependency,
                        "outcome", "success").increment();
                return result;
            } catch (RuntimeException exception) {
                last = exception;
                meters.counter("resilience.retries", "dependency", dependency).increment();
                if (attempt < properties.getMaxAttempts()) sleep(attempt);
            }
        }
        synchronized (state) {
            state.failures++;
            if (state.failures >= properties.getCircuitFailureThreshold()) {
                state.openUntil = clock.instant().plusSeconds(properties.getCircuitOpenSeconds());
            }
        }
        meters.counter("resilience.calls", "dependency", dependency,
                "outcome", "failed").increment();
        throw last;
    }

    private void sleep(int attempt) {
        try {
            sleeper.sleep(Math.multiplyExact(properties.getRetryDelayMillis(), attempt));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new DownstreamUnavailableException("Retry interrupted", exception);
        }
    }

    @FunctionalInterface
    interface Sleeper { void sleep(long millis) throws InterruptedException; }

    private static final class CircuitState {
        private int failures;
        private Instant openUntil;
        private void reset() { failures = 0; openUntil = null; }
    }

    public static final class DownstreamUnavailableException extends RuntimeException {
        public DownstreamUnavailableException(String message) { super(message); }
        public DownstreamUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
