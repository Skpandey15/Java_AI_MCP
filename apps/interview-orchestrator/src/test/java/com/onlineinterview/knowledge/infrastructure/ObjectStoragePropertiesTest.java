package com.onlineinterview.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectStoragePropertiesTest {
    @Test
    void exposesConfiguredValuesAndSafeDefaults() {
        var properties = new ObjectStorageProperties();
        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getEndpoint()).isEqualTo("http://localhost:9000");
        assertThat(properties.getBucket()).isEqualTo("knowledge-documents");
        assertThat(properties.getMaximumObjectBytes()).isEqualTo(10 * 1024 * 1024);

        properties.setEnabled(true);
        properties.setEndpoint("http://minio:9000");
        properties.setAccessKey("access");
        properties.setSecretKey("secret");
        properties.setBucket("bucket");
        properties.setMaximumObjectBytes(42);

        assertThat(properties.isEnabled()).isTrue();
        assertThat(properties.getEndpoint()).isEqualTo("http://minio:9000");
        assertThat(properties.getAccessKey()).isEqualTo("access");
        assertThat(properties.getSecretKey()).isEqualTo("secret");
        assertThat(properties.getBucket()).isEqualTo("bucket");
        assertThat(properties.getMaximumObjectBytes()).isEqualTo(42);
    }
}
