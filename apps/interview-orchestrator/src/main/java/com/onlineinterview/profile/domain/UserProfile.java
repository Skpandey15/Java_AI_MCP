package com.onlineinterview.profile.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_profile")
public class UserProfile {
    @Id
    private UUID id;

    @Column(name = "identity_subject", nullable = false, unique = true)
    private String identitySubject;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected UserProfile() {
    }

    public static UserProfile registerCandidate(String subject, String email, String displayName) {
        return registerCandidate("default", subject, email, displayName);
    }

    public static UserProfile registerCandidate(
            String tenantId, String subject, String email, String displayName) {
        Instant now = Instant.now();
        UserProfile profile = new UserProfile();
        profile.id = UUID.randomUUID();
        profile.tenantId = tenantId;
        profile.identitySubject = subject;
        profile.email = email;
        profile.displayName = displayName;
        profile.role = UserRole.CANDIDATE;
        profile.status = UserStatus.ACTIVE;
        profile.createdAt = now;
        profile.updatedAt = now;
        return profile;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getIdentitySubject() { return identitySubject; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public UserRole getRole() { return role; }
    public UserStatus getStatus() { return status; }
}
