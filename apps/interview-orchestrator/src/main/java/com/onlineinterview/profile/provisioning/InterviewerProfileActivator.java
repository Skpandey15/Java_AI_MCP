package com.onlineinterview.profile.provisioning;

import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.domain.UserRole;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.CONFLICT;

@Service
public class InterviewerProfileActivator {
    private final UserProfileRepository profiles;
    private final IdentityProvisioningRepository provisioning;
    private final IdentityProvisioningAuditRepository audit;
    public InterviewerProfileActivator(UserProfileRepository profiles,
            IdentityProvisioningRepository provisioning,
            IdentityProvisioningAuditRepository audit) {
        this.profiles = profiles; this.provisioning = provisioning; this.audit = audit;
    }

    @Transactional
    public void activate(IdentityProvisioning operation, String actor) {
        var bySubject = profiles.findByIdentitySubject(operation.getIdentitySubject());
        var profile = bySubject.orElse(null);
        if (profile == null) {
            var byEmail = profiles.findByTenantIdAndEmailIgnoreCase(
                    operation.getTenantId(), operation.getEmail());
            if (byEmail.isPresent()) {
                throw new ResponseStatusException(CONFLICT,
                        "Email is already bound to another identity subject");
            }
        }
        if (profile != null && profile.getRole() != UserRole.INTERVIEWER) {
            throw new ResponseStatusException(CONFLICT, "Application profile has a conflicting role");
        }
        if (profile == null) {
            profiles.save(UserProfile.registerInterviewer(operation.getTenantId(),
                    operation.getIdentitySubject(), operation.getEmail(), operation.getDisplayName()));
        }
        operation.activate(); provisioning.save(operation);
        audit.save(new IdentityProvisioningAudit(operation.getId(), "ACTIVATED", actor, null));
    }
}
