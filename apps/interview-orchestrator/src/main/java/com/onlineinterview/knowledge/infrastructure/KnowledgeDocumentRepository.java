package com.onlineinterview.knowledge.infrastructure;

import com.onlineinterview.knowledge.domain.KnowledgeDocument;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, UUID> {
    List<KnowledgeDocument> findByCollectionIdOrderByCreatedAtDesc(UUID collectionId);
}
