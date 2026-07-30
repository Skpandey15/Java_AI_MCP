package com.onlineinterview.knowledge.infrastructure;

import com.onlineinterview.knowledge.domain.KnowledgeChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, UUID> {
    List<KnowledgeChunk> findByDocumentIdOrderByIndexAsc(UUID documentId);
    void deleteByDocumentId(UUID documentId);
}
