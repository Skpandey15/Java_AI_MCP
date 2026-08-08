package com.onlineinterview.session.adaptive;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Candidate-facing endpoints for an adaptive interview. Gated by app.adaptive.enabled in the
 *  service (returns 404 when the feature is off), so this is inert until Phase 3 turns it on. */
@RestController
@RequestMapping("/api/v1/candidate/adaptive-sessions")
public class AdaptiveSessionController {
    private final AdaptiveSessionService service;

    public AdaptiveSessionController(AdaptiveSessionService service) {
        this.service = service;
    }

    @PostMapping("/{assignmentId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AdaptiveSessionService.AdaptiveView start(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
        return service.start(jwt.getSubject(), assignmentId);
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AdaptiveSessionService.AdaptiveView load(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.load(jwt.getSubject(), sessionId);
    }

    @PostMapping("/{sessionId}/answer")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AdaptiveSessionService.AdaptiveView answer(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId, @Valid @RequestBody AnswerBody body) {
        return service.answer(jwt.getSubject(), sessionId, body.answer());
    }

    public record AnswerBody(@Size(max = 20000) String answer) {}
}
