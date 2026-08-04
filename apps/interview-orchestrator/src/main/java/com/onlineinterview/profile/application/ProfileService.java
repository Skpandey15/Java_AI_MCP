package com.onlineinterview.profile.application;

import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.domain.UserStatus;
import java.util.List;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProfileService {
    private final UserProfileRepository repository;

    public ProfileService(UserProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<UserProfile> findBySubject(String subject) {
        return repository.findByIdentitySubject(subject);
    }

    @Transactional(readOnly = true)
    public List<UserProfile> activeCandidates(String tenantId) {
        return repository.findByTenantIdAndRoleAndStatusOrderByDisplayNameAsc(
                tenantId, UserRole.CANDIDATE, UserStatus.ACTIVE);
    }

    @Transactional
    public UserProfile registerCandidate(String subject, String email, String displayName) {
        return registerCandidate("default", subject, email, displayName);
    }

    @Transactional
    public UserProfile registerCandidate(
            String tenantId, String subject, String email, String displayName) {
        return repository.findByIdentitySubject(subject)
                .map(existing -> {
                    if (!existing.getTenantId().equals(tenantId)) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Identity is already registered to another tenant");
                    }
                    if (existing.getRole() != UserRole.CANDIDATE) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Identity is already registered with a different application role");
                    }
                    if (!existing.getEmail().equalsIgnoreCase(email.strip())) {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Identity email does not match the registered application profile");
                    }
                    return existing;
                })
                .orElseGet(() -> repository.save(
                        UserProfile.registerCandidate(
                                tenantId, subject, email.strip().toLowerCase(java.util.Locale.ROOT),
                                displayName.strip())));
    }
}
