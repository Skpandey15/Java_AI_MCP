package com.onlineinterview.review.api;

import com.onlineinterview.common.api.PageResponse;
import com.onlineinterview.review.application.ReviewService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Validated
public class ReviewController {
    private final ReviewService service;
    private final com.onlineinterview.notification.CandidateResultMailService resultMail;

    public ReviewController(ReviewService service,
            com.onlineinterview.notification.CandidateResultMailService resultMail) {
        this.service = service;
        this.resultMail = resultMail;
    }

    @GetMapping("/interviewer/submissions")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public PageResponse<SubmissionSummaryResponse> queue(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID candidateId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.queue(jwt.getSubject(), candidateId, page, size);
    }

    @GetMapping("/interviewer/submissions/{sessionId}")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public SubmissionDetailResponse detail(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        return service.detail(jwt.getSubject(), sessionId);
    }

    @PutMapping("/interviewer/submissions/{sessionId}/answers/{answerId}/score")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public SubmissionDetailResponse score(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId, @PathVariable UUID answerId,
            @Valid @RequestBody ScoreAnswerRequest request) {
        return service.score(jwt.getSubject(), sessionId, answerId,
                request.score(), request.feedback());
    }

    @PostMapping("/interviewer/submissions/{sessionId}/finalize")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public SubmissionDetailResponse finalizeReview(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId, @Valid @RequestBody FinalizeReviewRequest request) {
        return service.finalizeReview(jwt.getSubject(), sessionId, request.feedback());
    }

    @PostMapping("/interviewer/submissions/{sessionId}/ai-suggest")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public java.util.List<AssessmentResponses.AnswerSuggestion> aiSuggest(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.suggestScores(jwt.getSubject(), sessionId);
    }

    @PostMapping("/interviewer/submissions/{sessionId}/answer-key")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public SubmissionDetailResponse generateAnswerKey(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.generateAnswerKey(jwt.getSubject(), sessionId);
    }

    @PostMapping("/interviewer/submissions/{sessionId}/questions/{questionId}/answer-key")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public SubmissionDetailResponse generateQuestionAnswerKey(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId,
            @PathVariable UUID questionId) {
        return service.generateQuestionAnswerKey(jwt.getSubject(), sessionId, questionId);
    }

    @PostMapping("/interviewer/submissions/{sessionId}/coaching")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public AssessmentResponses.CoachingResponse draftCoaching(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.draftCoaching(jwt.getSubject(), sessionId);
    }

    @PostMapping("/interviewer/submissions/{sessionId}/coaching:approve")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public AssessmentResponses.CoachingResponse approveCoaching(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return service.approveCoaching(jwt.getSubject(), sessionId);
    }

    @PostMapping("/interviewer/submissions/{sessionId}/email-result")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public EmailResultResponse emailResult(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return new EmailResultResponse(resultMail.sendResult(jwt.getSubject(), sessionId));
    }

    /** Confirms the recipient the result email was sent to. */
    public record EmailResultResponse(String sentTo) {}

    @GetMapping("/candidate/sessions/{sessionId}/result")
    @PreAuthorize("hasRole('CANDIDATE')")
    public CandidateResultResponse candidateResult(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId) {
        return service.candidateResult(jwt.getSubject(), sessionId);
    }
}
