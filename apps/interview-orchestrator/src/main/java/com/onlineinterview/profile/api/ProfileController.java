package com.onlineinterview.profile.api;

import com.onlineinterview.profile.application.ProfileService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.List;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import com.onlineinterview.shared.security.TenantClaim;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {
    private final ProfileService profileService;
    private final boolean requireVerifiedEmail;

    public ProfileController(ProfileService profileService,
            @Value("${app.identity.require-verified-email:true}") boolean requireVerifiedEmail) {
        this.profileService = profileService;
        this.requireVerifiedEmail = requireVerifiedEmail;
    }

    @GetMapping("/me")
    public ProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.findBySubject(jwt.getSubject())
                .map(ProfileResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND, "Application profile registration is incomplete"));
    }

    @GetMapping("/candidates")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<ProfileResponse> candidates(@AuthenticationPrincipal Jwt jwt) {
        return profileService.activeCandidates(TenantClaim.required(jwt))
                .stream().map(ProfileResponse::from).toList();
    }

    @PostMapping("/profiles/registration-complete")
    @PreAuthorize("hasRole('CANDIDATE') and !hasRole('INTERVIEWER')")
    public ResponseEntity<ProfileResponse> completeRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @Validated @RequestBody RegistrationRequest request) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank() || (requireVerifiedEmail
                && !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))) {
            throw new ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "A verified email claim is required");
        }
        var profile = profileService.registerCandidate(
                TenantClaim.required(jwt), jwt.getSubject(), email, request.displayName());
        return ResponseEntity.created(URI.create("/api/v1/me")).body(ProfileResponse.from(profile));
    }
}
