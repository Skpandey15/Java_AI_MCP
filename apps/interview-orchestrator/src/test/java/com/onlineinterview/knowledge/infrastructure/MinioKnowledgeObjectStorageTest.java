package com.onlineinterview.knowledge.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class MinioKnowledgeObjectStorageTest {
    private final ObjectStorageProperties properties = properties();
    private final MinioClient client = mock(MinioClient.class);
    private final MinioKnowledgeObjectStorage storage =
            new MinioKnowledgeObjectStorage(properties, client);

    @Test
    void createsMissingBucketAndAcceptsExistingBucket() throws Exception {
        when(client.bucketExists(any())).thenReturn(false, true);
        storage.initializeBucket();
        storage.initializeBucket();
        verify(client).makeBucket(any());
    }

    @Test
    void rejectsBucketInitializationFailure() throws Exception {
        when(client.bucketExists(any())).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(storage::initializeBucket)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to initialize knowledge object bucket");
    }

    @Test
    void storesContentWithSafeKeySizeAndDigest() throws Exception {
        var result = storage.put("owner/unsafe", "../source.md", "text/markdown",
                "content".getBytes(StandardCharsets.UTF_8));

        assertThat(result.key()).startsWith("owner_unsafe/").endsWith("/.._source.md");
        assertThat(result.size()).isEqualTo(7);
        assertThat(result.sha256()).hasSize(64);
        verify(client).putObject(any());
    }

    @Test
    void enforcesSizeAndWrapsUploadFailure() throws Exception {
        assertThatThrownBy(() -> storage.put("owner", "a", "text/plain", new byte[0]))
                .isInstanceOf(IllegalArgumentException.class);
        properties.setMaximumObjectBytes(1);
        assertThatThrownBy(() -> storage.put("owner", "a", "text/plain", new byte[2]))
                .isInstanceOf(IllegalArgumentException.class);

        properties.setMaximumObjectBytes(100);
        when(client.putObject(any())).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> storage.put("owner", "a", "text/plain", new byte[] {1}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to store knowledge document");
    }

    @Test
    void readsAndDeletesObjectsAndWrapsFailures() throws Exception {
        var response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn("data".getBytes(StandardCharsets.UTF_8));
        when(client.getObject(any())).thenReturn(response);
        assertThat(storage.get("key")).isEqualTo("data".getBytes(StandardCharsets.UTF_8));
        verify(response).close();

        storage.delete("key");
        verify(client).removeObject(any());

        when(client.getObject(any())).thenThrow(new RuntimeException("down"));
        assertThatThrownBy(() -> storage.get("key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to read knowledge document");
        doThrow(new RuntimeException("down")).when(client).removeObject(any());
        assertThatThrownBy(() -> storage.delete("key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to delete knowledge document");
    }

    @Test
    void rejectsOversizedStoredObject() throws Exception {
        properties.setMaximumObjectBytes(1);
        var response = mock(GetObjectResponse.class);
        when(response.readAllBytes()).thenReturn(new byte[] {1, 2});
        when(client.getObject(any())).thenReturn(response);
        assertThatThrownBy(() -> storage.get("key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Unable to read knowledge document")
                .hasRootCauseMessage("Stored knowledge document exceeds size limit");
    }

    @Test
    void reportsObjectStorageHealth() throws Exception {
        when(client.bucketExists(any())).thenReturn(true, false);
        assertThat(storage.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(storage.health().getStatus().getCode()).isEqualTo("DOWN");
        when(client.bucketExists(any())).thenThrow(new RuntimeException("down"));
        assertThat(storage.health().getStatus().getCode()).isEqualTo("DOWN");
    }

    private static ObjectStorageProperties properties() {
        var result = new ObjectStorageProperties();
        result.setBucket("knowledge");
        result.setMaximumObjectBytes(100);
        return result;
    }
}
