package com.onlineinterview.profile.provisioning;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identity_provisioning")
public class IdentityProvisioning {
    @Id private UUID id;
    @Column(name = "idempotency_key", nullable = false, unique = true) private String idempotencyKey;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(nullable = false) private String email;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "requested_role", nullable = false) private String requestedRole;
    @Column(name = "identity_subject") private String identitySubject;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private ProvisioningStatus status;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "failure_message") private String failureMessage;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "requested_by", nullable = false) private String requestedBy;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected IdentityProvisioning() {}

    public static IdentityProvisioning pending(String key, String tenantId, String email,
            String displayName, String requestedBy) {
        var now = Instant.now();
        var value = new IdentityProvisioning();
        value.id = UUID.randomUUID();
        value.idempotencyKey = key;
        value.tenantId = tenantId;
        value.email = email;
        value.displayName = displayName;
        value.requestedRole = "INTERVIEWER";
        value.status = ProvisioningStatus.PENDING;
        value.requestedBy = requestedBy;
        value.createdAt = now;
        value.updatedAt = now;
        return value;
    }

    public void beginAttempt() { attemptCount++; updatedAt = Instant.now(); }
    public void identityReady(String subject) {
        identitySubject = subject; status = ProvisioningStatus.IDENTITY_READY;
        failureCode = null; failureMessage = null; updatedAt = Instant.now();
    }
    public void activate() { status = ProvisioningStatus.ACTIVE; updatedAt = Instant.now(); }
    public void fail(String code, String message) {
        status = ProvisioningStatus.FAILED; failureCode = code;
        failureMessage = message == null ? null : message.substring(0, Math.min(500, message.length()));
        updatedAt = Instant.now();
    }
    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getTenantId() { return tenantId; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getIdentitySubject() { return identitySubject; }
    public ProvisioningStatus getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public int getAttemptCount() { return attemptCount; }
}
