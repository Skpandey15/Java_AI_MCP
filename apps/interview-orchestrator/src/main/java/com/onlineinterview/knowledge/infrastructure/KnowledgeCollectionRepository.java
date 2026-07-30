package com.onlineinterview.knowledge.infrastructure;

import com.onlineinterview.knowledge.domain.KnowledgeCollection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeCollectionRepository extends JpaRepository<KnowledgeCollection, UUID> {
    List<KnowledgeCollection> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);
}
