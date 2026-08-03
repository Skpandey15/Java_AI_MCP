package com.onlineinterview.session.infrastructure;

import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.domain.ReviewStatus;
import com.onlineinterview.session.domain.SessionState;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, UUID> {
    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Optional<InterviewSession> findFirstByAssignmentIdAndCandidateIdAndState(
            UUID assignmentId, UUID candidateId, SessionState state);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Optional<InterviewSession> findByIdAndCandidateId(UUID id, UUID candidateId);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    List<InterviewSession> findByAssignment_InterviewDefinition_OwnerSubjectAndStateOrderBySubmittedAtDesc(
            String ownerSubject, SessionState state);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Page<InterviewSession> findByAssignment_InterviewDefinition_OwnerSubjectAndState(
            String ownerSubject, SessionState state, Pageable pageable);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Page<InterviewSession> findByAssignment_InterviewDefinition_OwnerSubjectAndStateAndCandidateId(
            String ownerSubject, SessionState state, UUID candidateId, Pageable pageable);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    Optional<InterviewSession> findByIdAndAssignment_InterviewDefinition_OwnerSubject(
            UUID id, String ownerSubject);

    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    List<InterviewSession> findByCandidateIdOrderByStartedAtDesc(UUID candidateId);

    long countByAssignmentId(UUID assignmentId);

    // Un-submitted sessions whose time has run out — the scheduled sweep auto-finalizes these.
    @EntityGraph(attributePaths = {"assignment", "assignment.interviewDefinition"})
    List<InterviewSession> findByExpiresAtBeforeAndReviewStatus(
            Instant cutoff, ReviewStatus reviewStatus);
}
