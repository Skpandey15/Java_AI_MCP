package com.onlineinterview.profile.infrastructure;

import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.domain.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByIdentitySubject(String identitySubject);
    Optional<UserProfile> findByTenantIdAndEmailIgnoreCase(String tenantId, String email);
    List<UserProfile> findByTenantIdAndRoleAndStatusOrderByDisplayNameAsc(
            String tenantId, UserRole role, UserStatus status);
}
