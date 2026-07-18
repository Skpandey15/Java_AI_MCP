package com.onlineinterview.interview.infrastructure;

import com.onlineinterview.interview.domain.InterviewAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAssignmentRepository extends JpaRepository<InterviewAssignment, UUID> {
    @EntityGraph(attributePaths = "interviewDefinition")
    List<InterviewAssignment> findByCandidateIdOrderByStartsAtAsc(UUID candidateId);

    @EntityGraph(attributePaths = "interviewDefinition")
    Optional<InterviewAssignment> findByIdAndCandidateId(UUID id, UUID candidateId);
}
