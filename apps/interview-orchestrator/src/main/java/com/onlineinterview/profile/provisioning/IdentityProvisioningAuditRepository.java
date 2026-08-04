package com.onlineinterview.profile.provisioning;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityProvisioningAuditRepository extends JpaRepository<IdentityProvisioningAudit, UUID> {}
