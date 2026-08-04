package com.onlineinterview.profile.provisioning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_provisioning_audit")
public class IdentityProvisioningAudit {
    @Id private UUID id;
    @Column(name = "provisioning_id", nullable = false) private UUID provisioningId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @Column(name = "actor_subject", nullable = false) private String actorSubject;
    @Column private String detail;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected IdentityProvisioningAudit() {}
    public IdentityProvisioningAudit(UUID provisioningId, String eventType, String actor, String detail) {
        this.id = UUID.randomUUID(); this.provisioningId = provisioningId;
        this.eventType = eventType; this.actorSubject = actor; this.detail = detail;
        this.createdAt = Instant.now();
    }
}
