package com.onlineinterview.review.application;

import com.onlineinterview.common.api.PageResponse;
import com.onlineinterview.profile.domain.UserProfile;
import com.onlineinterview.profile.infrastructure.UserProfileRepository;
import com.onlineinterview.review.api.CandidateResultResponse;
import com.onlineinterview.review.api.ReviewQuestionResponse;
import com.onlineinterview.session.api.QuestionCitationResponse;
import com.onlineinterview.review.api.SubmissionDetailResponse;
import com.onlineinterview.review.api.SubmissionSummaryResponse;
import com.onlineinterview.messaging.application.OutboxService;
import com.onlineinterview.review.domain.ReviewAuditEvent;
import com.onlineinterview.review.infrastructure.ReviewAuditEventRepository;
import com.onlineinterview.session.domain.InterviewAnswer;
import com.onlineinterview.session.domain.InterviewSession;
import com.onlineinterview.session.domain.ReviewStatus;
import com.onlineinterview.session.domain.SessionState;
import com.onlineinterview.session.infrastructure.InterviewAnswerRepository;
import com.onlineinterview.session.infrastructure.InterviewSessionRepository;
import com.onlineinterview.session.infrastructure.ManualQuestionRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {
    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);
    private final InterviewSessionRepository sessions;
    private final InterviewAnswerRepository answers;
    private final ManualQuestionRepository questions;
    private final UserProfileRepository profiles;
    private final ReviewAuditEventRepository auditEvents;
    private final OutboxService outbox;

    public ReviewService(InterviewSessionRepository sessions, InterviewAnswerRepository answers,
            ManualQuestionRepository questions, UserProfileRepository profiles,
            ReviewAuditEventRepository auditEvents, OutboxService outbox) {
        this.sessions = sessions;
        this.answers = answers;
        this.questions = questions;
        this.profiles = profiles;
        this.auditEvents = auditEvents;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public PageResponse<SubmissionSummaryResponse> queue(
            String ownerSubject, int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedAt"));
        var sessionPage = sessions.findByAssignment_InterviewDefinition_OwnerSubjectAndState(
                ownerSubject, SessionState.SUBMITTED, pageable);
        var candidateIds = sessionPage.stream().map(InterviewSession::getCandidateId).collect(Collectors.toSet());
        Map<UUID, UserProfile> candidates = profiles.findAllById(candidateIds).stream()
                .collect(Collectors.toMap(UserProfile::getId, Function.identity()));
        var definitionIds = sessionPage.stream()
                .map(session -> session.getAssignment().getInterviewDefinition().getId())
                .collect(Collectors.toSet());
        Map<UUID, Integer> maxScores = questions.findByInterviewDefinition_IdIn(definitionIds).stream()
                .collect(Collectors.groupingBy(
                        question -> question.getInterviewDefinition().getId(),
                        Collectors.summingInt(question -> question.getMaxScore())));
        var summaries = sessionPage.map(session -> {
            var candidate = candidates.get(session.getCandidateId());
            if (candidate == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Candidate profile not found");
            }
            var definitionId = session.getAssignment().getInterviewDefinition().getId();
            return summary(session, candidate, maxScores.getOrDefault(definitionId, 0));
        });
        return PageResponse.from(summaries);
    }

    @Transactional(readOnly = true)
    public SubmissionDetailResponse detail(String ownerSubject, UUID sessionId) {
        return detail(ownedSubmission(ownerSubject, sessionId));
    }

    @Transactional
    public SubmissionDetailResponse score(String ownerSubject, UUID sessionId, UUID answerId,
            int score, String feedback) {
        var session = ownedPendingSubmission(ownerSubject, sessionId);
        var answer = answers.findByIdAndSession_Id(answerId, sessionId)
                .filter(item -> item.getQuestion().getInterviewDefinition().getId()
                        .equals(session.getAssignment().getInterviewDefinition().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Answer not found"));
        if (answer.isAutoScored()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Objective answers are scored automatically");
        }
        try {
            answer.review(score, feedback);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        auditEvents.save(ReviewAuditEvent.answerScored(
                sessionId, answerId, ownerSubject, score, feedback, Instant.now()));
        log.atInfo().addKeyValue("event", "review.answer_scored")
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("answerId", answerId)
                .log("Submission answer scored");
        return detail(session);
    }

    @Transactional
    public SubmissionDetailResponse finalizeReview(String ownerSubject, UUID sessionId,
            String feedback) {
        var session = ownedPendingSubmission(ownerSubject, sessionId);
        var answerList = answers.findBySession_Id(sessionId);
        boolean unscoredText = answerList.stream()
                .filter(answer -> !answer.isAutoScored())
                .anyMatch(answer -> answer.getAwardedScore() == null);
        if (unscoredText) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Every submitted text answer must be scored before finalization");
        }
        int total = answerList.stream().map(InterviewAnswer::getAwardedScore)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        var definition = session.getAssignment().getInterviewDefinition();
        int maxScore = questions.findByInterviewDefinitionIdOrderByOrderAsc(definition.getId())
                .stream().mapToInt(q -> q.getMaxScore()).sum();
        Instant now = Instant.now();
        try {
            session.finalizeReview(total, maxScore, definition.getPassingPercentage(),
                    feedback, ownerSubject, now);
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        auditEvents.save(ReviewAuditEvent.reviewFinalized(
                sessionId, ownerSubject, total, feedback, now));
        outbox.record("SESSION", sessionId, "review.finalized", Map.of(
                "sessionId", sessionId.toString(),
                "totalScore", total,
                "outcome", session.getResultOutcome().name()));
        log.atInfo().addKeyValue("event", "review.finalized")
                .addKeyValue("sessionId", sessionId)
                .addKeyValue("outcome", session.getResultOutcome())
                .log("Submission review finalized");
        return detail(session);
    }

    @Transactional(readOnly = true)
    public CandidateResultResponse candidateResult(String candidateSubject, UUID sessionId) {
        var candidate = profiles.findByIdentitySubject(candidateSubject)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Candidate profile registration is incomplete"));
        var session = sessions.findByIdAndCandidateId(sessionId, candidate.getId())
                .filter(item -> item.getState() == SessionState.SUBMITTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submitted session not found"));
        var definition = session.getAssignment().getInterviewDefinition();
        var questionList = questions.findByInterviewDefinitionIdOrderByOrderAsc(definition.getId());
        int maxScore = questionList.stream().mapToInt(q -> q.getMaxScore()).sum();
        if (session.getReviewStatus() != ReviewStatus.REVIEWED) {
            return new CandidateResultResponse(session.getId(), definition.getTitle(),
                    session.getSubmittedAt(), session.getReviewStatus().name(), null,
                    maxScore, definition.getPassingPercentage(), null,
                    null, null, List.of());
        }
        Map<UUID, InterviewAnswer> byQuestion = answers.findBySession_Id(sessionId).stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, Function.identity()));
        var results = questionList.stream().map(question -> {
            var answer = byQuestion.get(question.getId());
            return new CandidateResultResponse.CandidateAnswerResult(
                    question.getOrder(), question.getType().name(), question.getPrompt(),
                    answer == null ? "" : answer.getContent(), question.getMaxScore(),
                    answer == null ? 0 : answer.getAwardedScore(),
                    answer == null ? null : answer.getReviewerFeedback());
        }).toList();
        return new CandidateResultResponse(session.getId(), definition.getTitle(),
                session.getSubmittedAt(), session.getReviewStatus().name(),
                session.getTotalScore(), maxScore, definition.getPassingPercentage(),
                percentage(session.getTotalScore(), maxScore), outcome(session),
                session.getReviewFeedback(), results);
    }

    private SubmissionSummaryResponse summary(
            InterviewSession session, UserProfile candidate, int maxScore) {
        var definition = session.getAssignment().getInterviewDefinition();
        return new SubmissionSummaryResponse(session.getId(), definition.getTitle(),
                candidate.getDisplayName(), candidate.getEmail(), session.getSubmittedAt(),
                session.getReviewStatus().name(), session.getTotalScore(), maxScore,
                percentage(session.getTotalScore(), maxScore), outcome(session));
    }

    private SubmissionDetailResponse detail(InterviewSession session) {
        var definition = session.getAssignment().getInterviewDefinition();
        var candidate = profiles.findById(session.getCandidateId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Candidate profile not found"));
        var questionList = questions.findByInterviewDefinitionIdOrderByOrderAsc(definition.getId());
        Map<UUID, InterviewAnswer> byQuestion = answers.findBySession_Id(session.getId()).stream()
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, Function.identity()));
        var reviewQuestions = questionList.stream().map(question -> {
            var answer = byQuestion.get(question.getId());
            return new ReviewQuestionResponse(question.getId(), answer == null ? null : answer.getId(),
                    question.getOrder(), question.getType().name(), question.getPrompt(),
                    question.getOptions(), question.getCorrectAnswers(),
                    answer == null ? "" : answer.getContent(), question.getMaxScore(),
                    answer == null ? null : answer.getAwardedScore(),
                    answer == null ? null : answer.getReviewerFeedback(),
                    answer != null && answer.isAutoScored(),
                    question.getCitations().stream().map(QuestionCitationResponse::from).toList());
        }).toList();
        int maxScore = questionList.stream().mapToInt(q -> q.getMaxScore()).sum();
        return new SubmissionDetailResponse(session.getId(), definition.getTitle(),
                candidate.getDisplayName(), candidate.getEmail(), session.getSubmittedAt(),
                session.getReviewStatus().name(), session.getObjectiveScore(),
                session.getTotalScore(), maxScore, definition.getPassingPercentage(),
                percentage(session.getTotalScore(), maxScore), outcome(session),
                session.getReviewFeedback(), reviewQuestions);
    }

    private Integer percentage(Integer score, int maxScore) {
        return score == null || maxScore <= 0 ? null : (int) ((long) score * 100 / maxScore);
    }

    private String outcome(InterviewSession session) {
        return session.getResultOutcome() == null ? null : session.getResultOutcome().name();
    }

    private InterviewSession ownedSubmission(String ownerSubject, UUID sessionId) {
        return sessions.findByIdAndAssignment_InterviewDefinition_OwnerSubject(sessionId, ownerSubject)
                .filter(session -> session.getState() == SessionState.SUBMITTED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Submission not found"));
    }

    private InterviewSession ownedPendingSubmission(String ownerSubject, UUID sessionId) {
        var session = ownedSubmission(ownerSubject, sessionId);
        if (session.getReviewStatus() != ReviewStatus.PENDING_REVIEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Submission is not pending review");
        }
        return session;
    }
}
