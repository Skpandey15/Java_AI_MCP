package com.onlineinterview.profile.provisioning;

import java.util.UUID;

public record ProvisioningResponse(UUID id, String tenantId, String email, String displayName,
        String identitySubject, String status, int attemptCount, String failureCode) {
    static ProvisioningResponse from(IdentityProvisioning value) {
        return new ProvisioningResponse(value.getId(), value.getTenantId(), value.getEmail(),
                value.getDisplayName(), value.getIdentitySubject(), value.getStatus().name(),
                value.getAttemptCount(), value.getFailureCode());
    }
}
