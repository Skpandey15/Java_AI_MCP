package com.onlineinterview.knowledge.application;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.rag")
public class RagProperties {
    @DecimalMin("-1.0") @DecimalMax("1.0")
    private double minimumSimilarity = 0.55;
    @Min(1) @Max(20)
    private int retrievalLimit = 8;

    public double getMinimumSimilarity() { return minimumSimilarity; }
    public void setMinimumSimilarity(double value) { minimumSimilarity = value; }
    public int getRetrievalLimit() { return retrievalLimit; }
    public void setRetrievalLimit(int value) { retrievalLimit = value; }
}
