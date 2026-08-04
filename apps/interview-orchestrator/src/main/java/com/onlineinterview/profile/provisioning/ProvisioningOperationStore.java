package com.onlineinterview.profile.provisioning;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class ProvisioningOperationStore {
    private final IdentityProvisioningRepository provisioning;
    private final IdentityProvisioningAuditRepository audit;
    public ProvisioningOperationStore(IdentityProvisioningRepository provisioning,
            IdentityProvisioningAuditRepository audit) {
        this.provisioning = provisioning; this.audit = audit;
    }

    @Transactional
    public IdentityProvisioning createOrLoad(String key, String tenantId, String email,
            String displayName, String actor) {
        return provisioning.findByIdempotencyKey(key).map(existing -> {
            if (!existing.getTenantId().equals(tenantId) || !existing.getEmail().equals(email)) {
                throw new ResponseStatusException(CONFLICT, "Idempotency key was used for another request");
            }
            return existing;
        }).orElseGet(() -> {
            provisioning.findByTenantIdAndEmailIgnoreCase(tenantId, email).ifPresent(existing -> {
                throw new ResponseStatusException(CONFLICT, "Interviewer provisioning already exists");
            });
            try {
                var created = provisioning.save(IdentityProvisioning.pending(
                        key, tenantId, email, displayName, actor));
                audit.save(new IdentityProvisioningAudit(created.getId(), "REQUESTED", actor, null));
                return created;
            } catch (DataIntegrityViolationException ex) {
                throw new ResponseStatusException(CONFLICT, "Concurrent provisioning request", ex);
            }
        });
    }
}
