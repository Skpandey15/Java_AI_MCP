package com.onlineinterview.session.adaptive;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdaptiveSessionStateRepository extends JpaRepository<AdaptiveSessionState, UUID> {
}
