package com.onlineinterview.interview.infrastructure;

import com.onlineinterview.interview.domain.InterviewDefinition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewDefinitionRepository extends JpaRepository<InterviewDefinition, UUID> {
    List<InterviewDefinition> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);
}
