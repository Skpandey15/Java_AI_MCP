package com.onlineinterview.session.domain;

import com.onlineinterview.interview.domain.InterviewDefinition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "manual_question")
public class ManualQuestion {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interview_definition_id", nullable = false)
    private InterviewDefinition interviewDefinition;
    @Column(name = "question_order", nullable = false) private int order;
    @Column(nullable = false, length = 4000) private String prompt;
    @Column(name = "max_score", nullable = false) private int maxScore;

    protected ManualQuestion() {}

    public static ManualQuestion create(InterviewDefinition definition, int order, String prompt, int maxScore) {
        var question = new ManualQuestion();
        question.id = UUID.randomUUID();
        question.interviewDefinition = definition;
        question.order = order;
        question.prompt = prompt;
        question.maxScore = maxScore;
        return question;
    }

    public UUID getId() { return id; }
    public InterviewDefinition getInterviewDefinition() { return interviewDefinition; }
    public int getOrder() { return order; }
    public String getPrompt() { return prompt; }
    public int getMaxScore() { return maxScore; }
}
