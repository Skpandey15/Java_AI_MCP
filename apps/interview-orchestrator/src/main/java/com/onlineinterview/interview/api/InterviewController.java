package com.onlineinterview.interview.api;

import com.onlineinterview.interview.application.InterviewService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @PostMapping("/interviews")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ResponseEntity<InterviewResponse> create(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateInterviewRequest request) {
        var definition = service.create(jwt.getSubject(), request.title(), request.description(),
                request.skills(), request.difficulty(), request.questionMode(),
                request.durationMinutes(), request.questionCount(), request.passingPercentage());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + definition.getId()))
                .body(InterviewResponse.from(definition));
    }

    @GetMapping("/interviews")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<InterviewResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.listOwned(jwt.getSubject()).stream().map(InterviewResponse::from).toList();
    }

    @PostMapping("/interviews/{interviewId}/publish")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public InterviewResponse publish(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID interviewId) {
        return InterviewResponse.from(service.publish(jwt.getSubject(), interviewId));
    }

    @PostMapping("/interviews/{interviewId}/assignments")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ResponseEntity<AssignmentResponse> assign(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID interviewId,
            @Valid @RequestBody CreateAssignmentRequest request) {
        var assignment = service.assign(jwt.getSubject(), interviewId, request.candidateId(),
                request.startsAt(), request.endsAt(), request.maxAttempts());
        return ResponseEntity.created(URI.create("/api/v1/assignments/" + assignment.getId()))
                .body(AssignmentResponse.from(assignment));
    }

    @GetMapping("/candidate/interviews")
    @PreAuthorize("hasRole('CANDIDATE')")
    public List<AssignmentResponse> candidateInterviews(@AuthenticationPrincipal Jwt jwt) {
        return service.candidateAssignments(jwt.getSubject()).stream()
                .map(AssignmentResponse::from).toList();
    }
}
