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

@RestController
@RequestMapping("/api/v1")
public class ProfileController {
    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
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
    public List<ProfileResponse> candidates() {
        return profileService.activeCandidates().stream().map(ProfileResponse::from).toList();
    }

    @PostMapping("/profiles/registration-complete")
    public ResponseEntity<ProfileResponse> completeRegistration(
            @AuthenticationPrincipal Jwt jwt,
            @Validated @RequestBody RegistrationRequest request) {
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("A verified email claim is required");
        }
        var profile = profileService.registerCandidate(jwt.getSubject(), email, request.displayName());
        return ResponseEntity.created(URI.create("/api/v1/me")).body(ProfileResponse.from(profile));
    }
}
