package com.onlineinterview.knowledge.infrastructure;

public interface KnowledgeObjectStorage {
    StoredObject put(String owner, String fileName, String mediaType, byte[] content);
    byte[] get(String objectKey);
    void delete(String objectKey);

    record StoredObject(String key, long size, String sha256) {}
}
