package com.onlineinterview.session.adaptive;

import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Interviewer-facing read-only view of an adaptive submission's transcript. Adaptive answers live
 *  in {@code adaptive_turn} (not {@code interview_answer}), so the standard review page has nothing
 *  to show without this. Gated by {@code app.adaptive.enabled} in the service. */
@RestController
@RequestMapping("/api/v1/interviewer/adaptive-sessions")
public class AdaptiveReviewController {
    private final AdaptiveSessionService service;

    public AdaptiveReviewController(AdaptiveSessionService service) {
        this.service = service;
    }

    @GetMapping("/{sessionId}/transcript")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public AdaptiveSessionService.AdaptiveTranscript transcript(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.transcriptForInterviewer(jwt.getSubject(), sessionId);
    }
}
