package com.onlineinterview.profile.provisioning;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityProvisioningRepository extends JpaRepository<IdentityProvisioning, UUID> {
    Optional<IdentityProvisioning> findByIdempotencyKey(String idempotencyKey);
    Optional<IdentityProvisioning> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
    List<IdentityProvisioning> findTop100ByStatusOrderByUpdatedAtAsc(ProvisioningStatus status);
}
