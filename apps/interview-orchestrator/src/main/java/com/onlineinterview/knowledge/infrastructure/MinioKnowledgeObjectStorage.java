package com.onlineinterview.knowledge.infrastructure;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

@Component
@ConditionalOnProperty(name = "app.object-storage.enabled", havingValue = "true")
public class MinioKnowledgeObjectStorage implements KnowledgeObjectStorage, HealthIndicator {
    private final ObjectStorageProperties properties;
    private final MinioClient client;

    @Autowired
    public MinioKnowledgeObjectStorage(ObjectStorageProperties properties) {
        this(properties, MinioClient.builder().endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), properties.getSecretKey()).build());
    }

    MinioKnowledgeObjectStorage(ObjectStorageProperties properties, MinioClient client) {
        this.properties = properties;
        this.client = client;
    }

    @PostConstruct
    void initializeBucket() {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket()).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to initialize knowledge object bucket", exception);
        }
    }

    @Override
    public StoredObject put(String owner, String fileName, String mediaType, byte[] content) {
        if (content.length == 0 || content.length > properties.getMaximumObjectBytes()) {
            throw new IllegalArgumentException("Document size must be between 1 and "
                    + properties.getMaximumObjectBytes() + " bytes");
        }
        var key = safeSegment(owner) + "/" + UUID.randomUUID() + "/" + safeSegment(fileName);
        try {
            client.putObject(PutObjectArgs.builder().bucket(properties.getBucket()).object(key)
                    .contentType(mediaType).stream(new ByteArrayInputStream(content),
                            content.length, -1).build());
            return new StoredObject(key, content.length, sha256(content));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to store knowledge document", exception);
        }
    }

    @Override
    public byte[] get(String objectKey) {
        try (var stream = client.getObject(GetObjectArgs.builder()
                .bucket(properties.getBucket()).object(objectKey).build())) {
            var content = stream.readAllBytes();
            if (content.length > properties.getMaximumObjectBytes()) {
                throw new IllegalStateException("Stored knowledge document exceeds size limit");
            }
            return content;
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to read knowledge document", exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.getBucket()).object(objectKey).build());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to delete knowledge document", exception);
        }
    }

    @Override
    public Health health() {
        try {
            return client.bucketExists(BucketExistsArgs.builder()
                    .bucket(properties.getBucket()).build())
                    ? Health.up().build()
                    : Health.down().withDetail("reason", "knowledge bucket is missing").build();
        } catch (Exception exception) {
            return Health.down(exception).build();
        }
    }

    private static String safeSegment(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
