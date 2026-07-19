package com.onlineinterview.review.infrastructure;

import com.onlineinterview.review.domain.ReviewAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewAuditEventRepository extends JpaRepository<ReviewAuditEvent, UUID> {
}
