package com.onlineinterview.ai;

import com.onlineinterview.session.api.QuestionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AiQuestionController {
    private final AiQuestionService service;

    public AiQuestionController(AiQuestionService service) {
        this.service = service;
    }

    @PostMapping("/interviews/{interviewId}/questions:generate")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<QuestionResponse> generate(@AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        return service.generate(jwt.getSubject(), interviewId, requestId).stream()
                .map(QuestionResponse::from).toList();
    }

    @PostMapping("/interviews/{interviewId}/questions:compose")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ComposeResult compose(@AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        var outcome = service.compose(jwt.getSubject(), interviewId, requestId);
        return new ComposeResult(
                outcome.questions().stream().map(QuestionResponse::from).toList(),
                outcome.rounds(), outcome.trace());
    }

    /** Composed questions plus the agent's round count and critique trace. */
    public record ComposeResult(List<QuestionResponse> questions, int rounds, List<String> trace) {}

    @PostMapping("/topics:suggest")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public TopicsResponse suggestTopics(@org.springframework.web.bind.annotation.RequestBody
            SuggestTopicsRequest request) {
        return new TopicsResponse(
                service.suggestTopics(request.technologies(), request.difficulty()));
    }

    public record SuggestTopicsRequest(List<String> technologies, String difficulty) {}
    public record TopicsResponse(List<String> topics) {}
}
