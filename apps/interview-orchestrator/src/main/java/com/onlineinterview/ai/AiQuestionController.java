package com.onlineinterview.ai;

import com.onlineinterview.session.api.QuestionResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.ACCEPTED)
    public ComposeJobResponse compose(@AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable UUID interviewId,
            @RequestHeader("Idempotency-Key") UUID requestId) {
        return ComposeJobResponse.from(service.startCompose(jwt.getSubject(), interviewId, requestId));
    }

    @GetMapping("/interviews/composition-jobs/{jobId}")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ComposeJobResponse composeJob(@AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.PathVariable UUID jobId) {
        return ComposeJobResponse.from(service.composeJob(jwt.getSubject(), jobId));
    }

    /** Async composition job status: PENDING | RUNNING | SUCCEEDED | FAILED. */
    public record ComposeJobResponse(UUID jobId, String status, int questionCount, int rounds,
            String error) {
        static ComposeJobResponse from(
                com.onlineinterview.ai.CompositionJobStore.Job job) {
            return new ComposeJobResponse(job.id(), job.status(), job.questionCount(),
                    job.rounds(), job.error());
        }
    }

    @PostMapping("/topics:suggest")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'CANDIDATE')")
    public TopicsResponse suggestTopics(@org.springframework.web.bind.annotation.RequestBody
            SuggestTopicsRequest request) {
        return new TopicsResponse(
                service.suggestTopics(request.technologies(), request.difficulty()));
    }

    public record SuggestTopicsRequest(List<String> technologies, String difficulty) {}
    public record TopicsResponse(List<String> topics) {}

    @PostMapping("/education/details")
    @PreAuthorize("hasAnyRole('INTERVIEWER', 'CANDIDATE')")
    public AiQuestionClient.TopicDetailsResponse topicDetails(
            @org.springframework.web.bind.annotation.RequestBody TopicDetailsRequest request) {
        return service.topicDetails(request.ecosystem(), request.technology(), request.topic());
    }

    public record TopicDetailsRequest(String ecosystem, String technology, String topic) {}
}
