package com.onlineinterview.session.adaptive;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveTurnRepository extends JpaRepository<AdaptiveTurn, UUID> {
    List<AdaptiveTurn> findBySessionIdOrderByOrdinalAsc(UUID sessionId);

    Optional<AdaptiveTurn> findFirstBySessionIdAndAnswerTextIsNullOrderByOrdinalDesc(UUID sessionId);
}
