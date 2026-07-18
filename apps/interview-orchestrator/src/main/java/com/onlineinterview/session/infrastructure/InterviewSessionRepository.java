package com.onlineinterview.session.infrastructure;

import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.domain.SessionState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Optional<InterviewSession> findFirstByAssignmentIdAndCandidateIdAndState(
            UUID assignmentId, UUID candidateId, SessionState state);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Optional<InterviewSession> findByIdAndCandidateId(UUID id, UUID candidateId);

    long countByAssignmentId(UUID assignmentId);
}
