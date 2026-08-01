package com.onlineinterview.interview.infrastructure;

import com.onlineinterview.interview.domain.InterviewDefinition;
import com.onlineinterview.interview.domain.InterviewStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewDefinitionRepository extends JpaRepository<InterviewDefinition, UUID> {
    List<InterviewDefinition> findByOwnerSubjectOrderByCreatedAtDesc(String ownerSubject);

    List<InterviewDefinition> findByOwnerSubjectAndStatusNotOrderByCreatedAtDesc(
            String ownerSubject, InterviewStatus status);
}
