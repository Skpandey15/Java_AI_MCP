package com.onlineinterview.knowledge.application;

import com.onlineinterview.knowledge.domain.*;
import com.onlineinterview.knowledge.infrastructure.*;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class KnowledgeService {
    private final KnowledgeCollectionRepository collections;
    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final DocumentChunker chunker;
    private final KnowledgeEmbeddingClient embeddingClient;
    private final KnowledgeVectorStore vectorStore;

    public KnowledgeService(
            KnowledgeCollectionRepository collections, KnowledgeDocumentRepository documents,
            KnowledgeChunkRepository chunks, DocumentChunker chunker,
            KnowledgeEmbeddingClient embeddingClient, KnowledgeVectorStore vectorStore) {
        this.collections = collections;
        this.documents = documents;
        this.chunks = chunks;
        this.chunker = chunker;
        this.embeddingClient = embeddingClient;
        this.vectorStore = vectorStore;
    }

    @Transactional
    public KnowledgeCollection createCollection(String owner, String name, String description) {
        return collections.save(KnowledgeCollection.create(owner, name, description));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeCollection> listCollections(String owner) {
        return collections.findByOwnerSubjectOrderByCreatedAtDesc(owner);
    }

    @Transactional
    public KnowledgeDocument addDocument(
            String owner, UUID collectionId, String fileName, String mediaType, String content) {
        var collection = ownedCollection(owner, collectionId);
        return documents.save(KnowledgeDocument.pending(collection, fileName, mediaType, content));
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> listDocuments(String owner, UUID collectionId) {
        ownedCollection(owner, collectionId);
        return documents.findByCollectionIdOrderByCreatedAtDesc(collectionId);
    }

    @Transactional
    public KnowledgeDocument prepareDocument(String owner, UUID documentId) {
        var document = documents.findById(documentId)
                .filter(value -> value.getCollection().getOwnerSubject().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Knowledge document not found"));
        document.startProcessing();
        chunks.deleteByDocumentId(documentId);
        var prepared = chunker.chunk(document.getContent());
        if (prepared.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Document contains no ingestible text");
        }
        for (int index = 0; index < prepared.size(); index++) {
            chunks.save(KnowledgeChunk.create(document, index, prepared.get(index)));
        }
        return document;
    }

    @Transactional
    public KnowledgeDocument ingestDocument(String owner, UUID documentId) {
        var document = prepareDocument(owner, documentId);
        var prepared = chunks.findByDocumentIdOrderByIndexAsc(documentId);
        var response = embeddingClient.embed(
                prepared.stream().map(KnowledgeChunk::getContent).toList());
        if (response == null || response.embeddings() == null
                || response.embeddings().size() != prepared.size()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Embedding service returned an invalid response");
        }
        for (int index = 0; index < prepared.size(); index++) {
            vectorStore.store(prepared.get(index).getId(), response.embeddings().get(index));
        }
        document.markReady();
        return document;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeVectorStore.SearchHit> search(
            String owner, UUID collectionId, String query, int limit) {
        ownedCollection(owner, collectionId);
        var response = embeddingClient.embed(List.of(query));
        if (response == null || response.embeddings() == null
                || response.embeddings().size() != 1) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY, "Embedding service returned an invalid response");
        }
        return vectorStore.search(owner, collectionId, response.embeddings().getFirst(), limit);
    }

    private KnowledgeCollection ownedCollection(String owner, UUID id) {
        return collections.findById(id).filter(value -> value.getOwnerSubject().equals(owner))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Knowledge collection not found"));
    }
}
