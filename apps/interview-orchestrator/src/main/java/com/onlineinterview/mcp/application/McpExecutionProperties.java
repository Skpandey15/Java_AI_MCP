package com.onlineinterview.mcp.application;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.mcp.execution")
public class McpExecutionProperties {
    @Min(1) @Max(60) private int timeoutSeconds = 10;
    @Min(1) @Max(100) private int callsPerMinute = 20;

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int value) { timeoutSeconds = value; }
    public int getCallsPerMinute() { return callsPerMinute; }
    public void setCallsPerMinute(int value) { callsPerMinute = value; }
}
