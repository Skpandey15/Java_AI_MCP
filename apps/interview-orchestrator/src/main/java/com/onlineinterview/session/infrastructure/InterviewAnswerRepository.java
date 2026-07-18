package com.onlineinterview.session.infrastructure;

import com.onlineinterview.session.domain.InterviewAnswer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewAnswerRepository extends JpaRepository<InterviewAnswer, UUID> {
    @EntityGraph(attributePaths = "question")
    List<InterviewAnswer> findBySession_Id(UUID sessionId);
    Optional<InterviewAnswer> findBySession_IdAndQuestion_Id(UUID sessionId, UUID questionId);
}
