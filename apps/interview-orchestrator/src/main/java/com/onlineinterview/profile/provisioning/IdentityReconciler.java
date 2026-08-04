package com.onlineinterview.profile.provisioning;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.identity.reconciliation.enabled", havingValue = "true")
public class IdentityReconciler {
    private static final Logger log = LoggerFactory.getLogger(IdentityReconciler.class);
    private final IdentityProvisioningRepository provisioning;
    private final IdentityProvisioningAuditRepository audit;
    private final KeycloakIdentityAdminClient identities;
    private final MeterRegistry meters;
    public IdentityReconciler(IdentityProvisioningRepository provisioning,
            IdentityProvisioningAuditRepository audit, KeycloakIdentityAdminClient identities,
            MeterRegistry meters) {
        this.provisioning = provisioning; this.audit = audit;
        this.identities = identities; this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${app.identity.reconciliation.interval-ms:300000}")
    public void reconcile() {
        for (var operation : provisioning.findTop100ByStatusOrderByUpdatedAtAsc(
                ProvisioningStatus.ACTIVE)) {
            if (identities.hasExpectedIdentity(operation.getIdentitySubject(),
                    operation.getEmail(), operation.getTenantId())) continue;
            try {
                String subject = identities.ensureInterviewer(operation.getEmail(),
                        operation.getDisplayName(), operation.getTenantId());
                if (!subject.equals(operation.getIdentitySubject())) {
                    throw new IllegalStateException("Identity subject changed during reconciliation");
                }
                audit.save(new IdentityProvisioningAudit(operation.getId(), "DRIFT_REPAIRED",
                        "system:identity-reconciler", null));
                meters.counter("identity.reconciliation.repaired").increment();
            } catch (RuntimeException ex) {
                meters.counter("identity.reconciliation.failed").increment();
                log.error("Identity reconciliation failed for provisioningId={}",
                        operation.getId(), ex);
            }
        }
    }
}
