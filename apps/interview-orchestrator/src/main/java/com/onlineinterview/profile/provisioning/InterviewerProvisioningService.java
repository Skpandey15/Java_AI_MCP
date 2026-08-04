package com.onlineinterview.profile.provisioning;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InterviewerProvisioningService {
    private final IdentityProvisioningRepository provisioning;
    private final IdentityProvisioningAuditRepository audit;
    private final KeycloakIdentityAdminClient identities;
    private final InterviewerProfileActivator activator;
    private final ProvisioningOperationStore store;
    private final Counter success;
    private final Counter failure;

    public InterviewerProvisioningService(IdentityProvisioningRepository provisioning,
            IdentityProvisioningAuditRepository audit,
            KeycloakIdentityAdminClient identities, InterviewerProfileActivator activator,
            ProvisioningOperationStore store, MeterRegistry meters) {
        this.provisioning = provisioning; this.audit = audit;
        this.identities = identities;
        this.activator = activator;
        this.store = store;
        this.success = meters.counter("identity.provisioning.success", "role", "interviewer");
        this.failure = meters.counter("identity.provisioning.failure", "role", "interviewer");
    }

    public IdentityProvisioning provision(String key, String tenantId, String email,
            String displayName, String actor) {
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        IdentityProvisioning operation = store.createOrLoad(key, tenantId, normalizedEmail,
                displayName.strip(), actor);
        if (operation.getStatus() == ProvisioningStatus.ACTIVE) return operation;
        operation.beginAttempt(); provisioning.save(operation);
        try {
            String subject = identities.ensureInterviewer(normalizedEmail, displayName, tenantId);
            operation.identityReady(subject); provisioning.save(operation);
            activator.activate(operation, actor);
            success.increment();
            return operation;
        } catch (RuntimeException ex) {
            operation.fail(ex.getClass().getSimpleName(), ex.getMessage());
            provisioning.save(operation);
            audit.save(new IdentityProvisioningAudit(operation.getId(), "FAILED", actor,
                    operation.getFailureCode()));
            failure.increment();
            throw ex;
        }
    }

}
