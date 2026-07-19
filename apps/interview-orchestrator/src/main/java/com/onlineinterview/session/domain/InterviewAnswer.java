package com.onlineinterview.session.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "interview_answer", uniqueConstraints =
        @UniqueConstraint(name = "uq_session_question", columnNames = {"session_id", "question_id"}))
public class InterviewAnswer {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private InterviewSession session;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false) private ManualQuestion question;
    @Column(nullable = false, length = 12000) private String content;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "awarded_score") private Integer awardedScore;
    @Column(name = "reviewer_feedback", length = 4000) private String reviewerFeedback;
    @Column(name = "auto_scored", nullable = false) private boolean autoScored;
    @Version private long version;

    protected InterviewAnswer() {}

    public static InterviewAnswer create(InterviewSession session, ManualQuestion question,
            String content, Instant now) {
        var answer = new InterviewAnswer();
        answer.id = UUID.randomUUID();
        answer.session = session;
        answer.question = question;
        answer.content = content;
        answer.updatedAt = now;
        return answer;
    }

    public void scoreObjective() {
        if (question.getType() == QuestionType.MCQ_SINGLE) {
            awardedScore = question.getCorrectAnswers().contains(content) ? question.getMaxScore() : 0;
            autoScored = true;
        } else if (question.getType() == QuestionType.MCQ_MULTIPLE) {
            var selected = Set.copyOf(content.lines().filter(value -> !value.isBlank()).toList());
            awardedScore = selected.equals(Set.copyOf(question.getCorrectAnswers()))
                    ? question.getMaxScore() : 0;
            autoScored = true;
        }
    }

    public void review(int score, String feedback) {
        if (score < 0 || score > question.getMaxScore()) {
            throw new IllegalArgumentException("Score must be between 0 and " + question.getMaxScore());
        }
        awardedScore = score;
        reviewerFeedback = feedback;
        autoScored = false;
    }

    public void update(String content, long expectedVersion, Instant now) {
        if (version != expectedVersion) {
            throw new IllegalStateException("Answer was updated by another request");
        }
        this.content = content;
        this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getQuestionId() { return question.getId(); }
    public ManualQuestion getQuestion() { return question; }
    public String getContent() { return content; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }
    public Integer getAwardedScore() { return awardedScore; }
    public String getReviewerFeedback() { return reviewerFeedback; }
    public boolean isAutoScored() { return autoScored; }
}
