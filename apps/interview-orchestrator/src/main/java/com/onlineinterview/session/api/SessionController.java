package com.onlineinterview.session.api;

import com.onlineinterview.session.application.QuestionService;
import com.onlineinterview.session.application.SessionService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SessionController {
    private final QuestionService questionService;
    private final SessionService sessionService;

    public SessionController(QuestionService questionService, SessionService sessionService) {
        this.questionService = questionService;
        this.sessionService = sessionService;
    }

    @PostMapping("/interviews/{interviewId}/questions")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public ResponseEntity<QuestionResponse> addQuestion(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID interviewId, @Valid @RequestBody QuestionRequest request) {
        var question = questionService.add(jwt.getSubject(), interviewId,
                request.order(), request.prompt(), request.maxScore());
        return ResponseEntity.created(URI.create("/api/v1/interviews/" + interviewId
                + "/questions/" + question.getId())).body(QuestionResponse.from(question));
    }

    @GetMapping("/interviews/{interviewId}/questions")
    @PreAuthorize("hasRole('INTERVIEWER')")
    public List<QuestionResponse> questions(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID interviewId) {
        return questionService.list(jwt.getSubject(), interviewId).stream()
                .map(QuestionResponse::from).toList();
    }

    @PostMapping("/candidate/assignments/{assignmentId}/sessions")
    @PreAuthorize("hasRole('CANDIDATE')")
    public SessionResponse start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID assignmentId) {
        var session = sessionService.start(jwt.getSubject(), assignmentId);
        return SessionResponse.from(sessionService.load(jwt.getSubject(), session.getId()));
    }

    @GetMapping("/candidate/sessions/{sessionId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public SessionResponse load(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.load(jwt.getSubject(), sessionId));
    }

    @PutMapping("/candidate/sessions/{sessionId}/answers/{questionId}")
    @PreAuthorize("hasRole('CANDIDATE')")
    public AnswerResponse autosave(@AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID sessionId, @PathVariable UUID questionId,
            @Valid @RequestBody AnswerRequest request) {
        return AnswerResponse.from(sessionService.autosave(jwt.getSubject(), sessionId,
                questionId, request.content(), request.expectedVersion()));
    }

    @PostMapping("/candidate/sessions/{sessionId}/submit")
    @PreAuthorize("hasRole('CANDIDATE')")
    public SessionResponse submit(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID sessionId) {
        return SessionResponse.from(sessionService.submit(jwt.getSubject(), sessionId));
    }
}
