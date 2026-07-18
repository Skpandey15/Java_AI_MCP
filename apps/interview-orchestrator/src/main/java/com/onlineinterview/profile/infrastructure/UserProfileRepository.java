package com.onlineinterview.profile.infrastructure;

import com.onlineinterview.profile.domain.UserProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, UUID> {
    Optional<UserProfile> findByIdentitySubject(String identitySubject);
}
