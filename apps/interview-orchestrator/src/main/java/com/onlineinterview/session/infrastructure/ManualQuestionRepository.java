package com.onlineinterview.session.infrastructure;

import com.onlineinterview.session.domain.ManualQuestion;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualQuestionRepository extends JpaRepository<ManualQuestion, UUID> {
    List<ManualQuestion> findByInterviewDefinitionIdOrderByOrderAsc(UUID interviewDefinitionId);
    long countByInterviewDefinitionId(UUID interviewDefinitionId);
}
