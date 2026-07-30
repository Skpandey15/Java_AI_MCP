package com.onlineinterview.knowledge.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.object-storage")
public class ObjectStorageProperties {
    private boolean enabled;
    private String endpoint = "http://localhost:9000";
    private String accessKey = "interview";
    private String secretKey = "interview-secret";
    private String bucket = "knowledge-documents";
    private long maximumObjectBytes = 10 * 1024 * 1024;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public long getMaximumObjectBytes() { return maximumObjectBytes; }
    public void setMaximumObjectBytes(long maximumObjectBytes) {
        this.maximumObjectBytes = maximumObjectBytes;
    }
}
