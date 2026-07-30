package com.onlineinterview.profile.application;

import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.domain.UserStatus;
import java.util.List;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.CONFLICT,
                                "Identity is already registered to another tenant");
                    }
                    return existing;
                })
                .orElseGet(() -> repository.save(
                        UserProfile.registerCandidate(tenantId, subject, email, displayName)));
    }
}
