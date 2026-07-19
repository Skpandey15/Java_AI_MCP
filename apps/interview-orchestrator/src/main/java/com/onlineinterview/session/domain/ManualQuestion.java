package com.onlineinterview.session.domain;

import com.onlineinterview.interview.domain.InterviewDefinition;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
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
    @Enumerated(EnumType.STRING)
    @Column(name = "question_type", nullable = false) private QuestionType type;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_option", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "option_order")
    @Column(name = "option_value", nullable = false, length = 1000)
    private List<String> options = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "question_correct_answer", joinColumns = @JoinColumn(name = "question_id"))
    @OrderColumn(name = "answer_order")
    @Column(name = "answer_value", nullable = false, length = 1000)
    private List<String> correctAnswers = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false) private QuestionSource source;
    @Column(name = "generation_request_id") private UUID generationRequestId;
    @Column(name = "model_policy") private String modelPolicy;
    @Column(name = "prompt_version") private String promptVersion;

    protected ManualQuestion() {}

    public static ManualQuestion create(InterviewDefinition definition, int order, String prompt,
            int maxScore, QuestionType type, List<String> options, List<String> correctAnswers) {
        var question = new ManualQuestion();
        question.id = UUID.randomUUID();
        question.interviewDefinition = definition;
        question.order = order;
        question.prompt = prompt;
        question.maxScore = maxScore;
        question.type = type;
        question.options = new ArrayList<>(options);
        question.correctAnswers = new ArrayList<>(correctAnswers);
        question.source = QuestionSource.MANUAL;
        question.validate();
        return question;
    }

    public static ManualQuestion generated(InterviewDefinition definition, int order,
            String prompt, int maxScore, UUID generationRequestId,
            String modelPolicy, String promptVersion) {
        var question = create(definition, order, prompt, maxScore,
                QuestionType.LONG_TEXT, List.of(), List.of());
        question.source = QuestionSource.AI_DIRECT;
        question.generationRequestId = generationRequestId;
        question.modelPolicy = modelPolicy;
        question.promptVersion = promptVersion;
        return question;
    }

    public void validateAnswer(String content) {
        if (type == QuestionType.MCQ_SINGLE && !options.contains(content)) {
            throw new IllegalArgumentException("Answer must be one of the configured options");
        }
        if (type == QuestionType.MCQ_MULTIPLE) {
            var selected = content.lines().filter(value -> !value.isBlank()).toList();
            if (selected.isEmpty() || !options.containsAll(selected)) {
                throw new IllegalArgumentException("Answers must be configured options");
            }
        }
        if (type == QuestionType.SHORT_TEXT && content.length() > 1000) {
            throw new IllegalArgumentException("Short-text answers cannot exceed 1000 characters");
        }
    }

    public void update(int order, String prompt, int maxScore, QuestionType type,
            List<String> options, List<String> correctAnswers) {
        this.order = order;
        this.prompt = prompt;
        this.maxScore = maxScore;
        this.type = type;
        this.options = new ArrayList<>(options);
        this.correctAnswers = new ArrayList<>(correctAnswers);
        validate();
    }

    private void validate() {
        if (type == QuestionType.MCQ_SINGLE || type == QuestionType.MCQ_MULTIPLE) {
            if (options.size() < 2) {
                throw new IllegalArgumentException("MCQ questions require at least two options");
            }
            if (correctAnswers.isEmpty() || !options.containsAll(correctAnswers)) {
                throw new IllegalArgumentException("Correct answers must reference configured options");
            }
            if (type == QuestionType.MCQ_SINGLE && correctAnswers.size() != 1) {
                throw new IllegalArgumentException("Single-choice MCQ requires exactly one correct answer");
            }
        } else if (!options.isEmpty() || !correctAnswers.isEmpty()) {
            throw new IllegalArgumentException("Text questions cannot define options or correct answers");
        }
    }

    public UUID getId() { return id; }
    public InterviewDefinition getInterviewDefinition() { return interviewDefinition; }
    public int getOrder() { return order; }
    public String getPrompt() { return prompt; }
    public int getMaxScore() { return maxScore; }
    public QuestionType getType() { return type; }
    public List<String> getOptions() { return List.copyOf(options); }
    public List<String> getCorrectAnswers() { return List.copyOf(correctAnswers); }
    public QuestionSource getSource() { return source; }
    public UUID getGenerationRequestId() { return generationRequestId; }
    public String getModelPolicy() { return modelPolicy; }
    public String getPromptVersion() { return promptVersion; }
}
