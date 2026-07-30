package com.onlineinterview.knowledge.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.onlineinterview.knowledge.domain.KnowledgeCollection;
import com.onlineinterview.knowledge.infrastructure.KnowledgeCollectionRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeChunkRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeDocumentRepository;
import com.onlineinterview.knowledge.infrastructure.KnowledgeEmbeddingClient;
import com.onlineinterview.knowledge.infrastructure.KnowledgeVectorStore;
import com.onlineinterview.knowledge.infrastructure.KnowledgeObjectStorage;
import java.util.Optional;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class KnowledgeServiceTest {
    private final KnowledgeCollectionRepository collections =
            mock(KnowledgeCollectionRepository.class);
    private final KnowledgeDocumentRepository documents =
            mock(KnowledgeDocumentRepository.class);
    private final KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
    private final KnowledgeEmbeddingClient embeddingClient = mock(KnowledgeEmbeddingClient.class);
    private final KnowledgeVectorStore vectorStore = mock(KnowledgeVectorStore.class);
    private final KnowledgeService service =
            new KnowledgeService(collections, documents, chunks, new DocumentChunker(),
                    embeddingClient, vectorStore);

    @Test
    void createsPendingTextDocumentInOwnedCollection() {
        var collection = KnowledgeCollection.create("owner", "Java", "Approved references");
        when(collections.findById(collection.getId())).thenReturn(Optional.of(collection));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var document = service.addDocument(
                "owner", collection.getId(), "spring.md", "text/markdown", "# Spring");

        assertThat(document.getCollection()).isEqualTo(collection);
        assertThat(document.getStatus().name()).isEqualTo("PENDING");
        assertThat(document.getContent()).isEqualTo("# Spring");
        assertThat(document.getObjectKey()).isNull();
        assertThat(document.getObjectSize()).isNull();
        assertThat(document.getContentSha256()).isNull();
    }

    @Test
    void storesOriginalDocumentInObjectStorageAndCleansUpRolledBackUpload() {
        var storage = mock(KnowledgeObjectStorage.class);
        var storedService = new KnowledgeService(collections, documents, chunks,
                new DocumentChunker(), embeddingClient, vectorStore, storage);
        var collection = KnowledgeCollection.create("owner", "Stored", "References");
        when(collections.findById(collection.getId())).thenReturn(Optional.of(collection));
        when(storage.put(eq("owner"), eq("source.md"), eq("text/markdown"), any()))
                .thenReturn(new KnowledgeObjectStorage.StoredObject("owner/key", 7, "a".repeat(64)));
        when(documents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        TransactionSynchronizationManager.initSynchronization();
        try {
            var document = storedService.addDocument("owner", collection.getId(),
                    "source.md", "text/markdown", "content");
            assertThat(document.getContent()).isNull();
            assertThat(document.getObjectKey()).isEqualTo("owner/key");
            assertThat(document.getObjectSize()).isEqualTo(7);
            assertThat(document.getContentSha256()).isEqualTo("a".repeat(64));
            TransactionSynchronizationManager.getSynchronizations().forEach(value ->
                    value.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(storage).delete("owner/key");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void preparesStoredDocumentByReadingObjectContent() {
        var storage = mock(KnowledgeObjectStorage.class);
        var storedService = new KnowledgeService(collections, documents, chunks,
                new DocumentChunker(), embeddingClient, vectorStore, storage);
        var collection = KnowledgeCollection.create("owner", "Stored", "References");
        var digest = java.util.HexFormat.of().formatHex(sha256("content"));
        var document = com.onlineinterview.knowledge.domain.KnowledgeDocument.pendingStored(
                collection, "source.md", "text/markdown", "owner/key", 7, digest);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(storage.get("owner/key")).thenReturn("content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        storedService.prepareDocument("owner", document.getId());

        verify(chunks).save(argThat(value -> value.getContent().equals("content")));
    }

    @Test
    void rejectsStoredDocumentWithIntegrityMismatch() {
        var storage = mock(KnowledgeObjectStorage.class);
        var storedService = new KnowledgeService(collections, documents, chunks,
                new DocumentChunker(), embeddingClient, vectorStore, storage);
        var collection = KnowledgeCollection.create("owner", "Stored", "References");
        var document = com.onlineinterview.knowledge.domain.KnowledgeDocument.pendingStored(
                collection, "source.md", "text/markdown", "owner/key", 7, "a".repeat(64));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(storage.get("owner/key")).thenReturn("content".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storedService.prepareDocument("owner", document.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Knowledge document integrity check failed");
    }

    @Test
    void failsClosedWhenStoredDocumentBackendIsNotConfigured() {
        var collection = KnowledgeCollection.create("owner", "Stored", "References");
        var document = com.onlineinterview.knowledge.domain.KnowledgeDocument.pendingStored(
                collection, "source.md", "text/markdown", "owner/key", 7, "a".repeat(64));
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.prepareDocument("owner", document.getId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Knowledge object storage is not configured");
    }

    @Test
    void hidesAnotherInterviewersCollection() {
        var collection = KnowledgeCollection.create("owner-a", "Private", "Private references");
        when(collections.findById(collection.getId())).thenReturn(Optional.of(collection));

        assertThatThrownBy(() -> service.listDocuments("owner-b", collection.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));
        verifyNoInteractions(documents);
    }

    @Test
    void rejectsDocumentWithoutIngestibleText() {
        var collection = KnowledgeCollection.create("owner", "Java", "References");
        var document = com.onlineinterview.knowledge.domain.KnowledgeDocument.pending(
                collection, "empty.txt", "text/plain", "  ");
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.prepareDocument("owner", document.getId()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void embedsPreparedChunksAndMarksDocumentReady() {
        var collection = KnowledgeCollection.create("owner", "Java", "References");
        var document = com.onlineinterview.knowledge.domain.KnowledgeDocument.pending(
                collection, "source.txt", "text/plain", "content");
        var chunk = com.onlineinterview.knowledge.domain.KnowledgeChunk.create(
                document, 0, "content");
        var vector = Collections.nCopies(KnowledgeVectorStore.DIMENSIONS, 0.1);
        when(documents.findById(document.getId())).thenReturn(Optional.of(document));
        when(chunks.findByDocumentIdOrderByIndexAsc(document.getId())).thenReturn(List.of(chunk));
        when(embeddingClient.embed(List.of("content"))).thenReturn(
                new KnowledgeEmbeddingClient.EmbeddingResponse("model", List.of(vector)));

        var result = service.ingestDocument("owner", document.getId());

        assertThat(result.getStatus().name()).isEqualTo("READY");
        verify(vectorStore).store(chunk.getId(), vector);
    }

    @Test
    void performsOwnerFilteredSearchAndRejectsInvalidEmbeddingResponses() {
        var collection = KnowledgeCollection.create("owner", "Java", "References");
        var vector = Collections.nCopies(KnowledgeVectorStore.DIMENSIONS, 0.2);
        var hit = new KnowledgeVectorStore.SearchHit(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                "source.md", 0, "content", 0.9);
        when(collections.findById(collection.getId())).thenReturn(Optional.of(collection));
        when(embeddingClient.embed(List.of("query"))).thenReturn(
                new KnowledgeEmbeddingClient.EmbeddingResponse("model", List.of(vector)));
        when(vectorStore.search("owner", collection.getId(), vector, 3, -1))
                .thenReturn(List.of(hit));

        assertThat(service.search("owner", collection.getId(), "query", 3))
                .containsExactly(hit);

        when(embeddingClient.embed(List.of("bad"))).thenReturn(
                new KnowledgeEmbeddingClient.EmbeddingResponse("model", List.of()));
        assertThatThrownBy(() -> service.search("owner", collection.getId(), "bad", 3))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        exception -> assertThat(exception.getStatusCode().value()).isEqualTo(502));
    }

    private static byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
