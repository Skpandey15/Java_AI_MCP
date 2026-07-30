package com.onlineinterview.messaging.infrastructure;

import com.onlineinterview.messaging.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    @Query(value = """
            SELECT * FROM outbox_event
            WHERE status = 'PENDING' AND next_attempt_at <= :now
            ORDER BY created_at
            LIMIT 20
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockPublishable(@Param("now") Instant now);
}
