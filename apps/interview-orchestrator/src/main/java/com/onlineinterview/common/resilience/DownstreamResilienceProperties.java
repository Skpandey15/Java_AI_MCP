package com.onlineinterview.common.resilience;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.resilience")
public class DownstreamResilienceProperties {
    @Min(1) @Max(5) private int maxAttempts = 3;
    @Min(10) @Max(5000) private long retryDelayMillis = 200;
    @Min(1) @Max(20) private int circuitFailureThreshold = 5;
    @Min(1) @Max(300) private long circuitOpenSeconds = 30;

    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { maxAttempts = value; }
    public long getRetryDelayMillis() { return retryDelayMillis; }
    public void setRetryDelayMillis(long value) { retryDelayMillis = value; }
    public int getCircuitFailureThreshold() { return circuitFailureThreshold; }
    public void setCircuitFailureThreshold(int value) { circuitFailureThreshold = value; }
    public long getCircuitOpenSeconds() { return circuitOpenSeconds; }
    public void setCircuitOpenSeconds(long value) { circuitOpenSeconds = value; }
}
