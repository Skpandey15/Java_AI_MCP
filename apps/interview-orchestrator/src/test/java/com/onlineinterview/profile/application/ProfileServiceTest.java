package com.onlineinterview.profile.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class ProfileServiceTest {
    private final UserProfileRepository repository = mock(UserProfileRepository.class);
    private final ProfileService service = new ProfileService(repository);

    @Test
    void preventsMovingAnExistingIdentityAcrossTenants() {
        var existing = UserProfile.registerCandidate(
                "tenant-a", "subject", "person@example.com", "Person");
        when(repository.findByIdentitySubject("subject")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.registerCandidate(
                "tenant-b", "subject", "person@example.com", "Person"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("409");
    }

    @Test
    void listsOnlyActiveCandidatesFromRequestedTenant() {
        service.activeCandidates("tenant-a");
        verify(repository).findByTenantIdAndRoleAndStatusOrderByDisplayNameAsc(
                "tenant-a", com.onlineinterview.profile.domain.UserRole.CANDIDATE,
                com.onlineinterview.profile.domain.UserStatus.ACTIVE);
    }

    @Test
    void registersNewCandidateInsideClaimedTenant() {
        when(repository.findByIdentitySubject("subject")).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(UserProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.registerCandidate(
                "tenant-a", "subject", "person@example.com", " Person ");

        org.assertj.core.api.Assertions.assertThat(created.getTenantId()).isEqualTo("tenant-a");
        org.assertj.core.api.Assertions.assertThat(created.getIdentitySubject()).isEqualTo("subject");
        org.assertj.core.api.Assertions.assertThat(created.getDisplayName()).isEqualTo("Person");
        org.assertj.core.api.Assertions.assertThat(created.getRole().name()).isEqualTo("CANDIDATE");
        org.assertj.core.api.Assertions.assertThat(created.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void returnsExistingCandidateWithinSameTenant() {
        var existing = UserProfile.registerCandidate(
                "tenant-a", "subject", "person@example.com", "Person");
        when(repository.findByIdentitySubject("subject")).thenReturn(Optional.of(existing));

        org.assertj.core.api.Assertions.assertThat(service.registerCandidate(
                "tenant-a", "subject", "person@example.com", "Person")).isSameAs(existing);
    }
}
