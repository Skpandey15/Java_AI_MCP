package com.onlineinterview.interview.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "interview_definition")
public class InterviewDefinition {
    @Id private UUID id;
    @Column(name = "owner_subject", nullable = false) private String ownerSubject;
    @Column(nullable = false) private String title;
    @Column(nullable = false) private String description;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "interview_definition_skill", joinColumns = @JoinColumn(name = "interview_definition_id"))
    @OrderColumn(name = "skill_order")
    @Column(name = "skill", nullable = false)
    private List<String> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING) @Column(nullable = false) private InterviewDifficulty difficulty;
    @Enumerated(EnumType.STRING) @Column(name = "question_mode", nullable = false) private QuestionMode questionMode;
    @Column(name = "duration_minutes", nullable = false) private int durationMinutes;
    @Column(name = "question_count", nullable = false) private int questionCount;
    @Column(name = "mcq_single_count", nullable = false) private int mcqSingleCount;
    @Column(name = "mcq_multiple_count", nullable = false) private int mcqMultipleCount;
    @Column(name = "short_text_count", nullable = false) private int shortTextCount;
    @Column(name = "long_text_count", nullable = false) private int longTextCount;
    @Column(name = "passing_percentage", nullable = false) private int passingPercentage;
    @Column(name = "knowledge_collection_id") private UUID knowledgeCollectionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private InterviewStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private long version;

    protected InterviewDefinition() {}

    public static InterviewDefinition draft(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount) {
        return draft(ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, 70, QuestionComposition.allLongText(questionCount));
    }

    public static InterviewDefinition draft(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage) {
        return draft(ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, passingPercentage,
                QuestionComposition.allLongText(questionCount));
    }

    public static InterviewDefinition draft(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage,
            QuestionComposition composition) {
        return draft(ownerSubject, title, description, skills, difficulty, questionMode,
                durationMinutes, questionCount, passingPercentage, composition, null);
    }

    public static InterviewDefinition draft(String ownerSubject, String title, String description,
            List<String> skills, InterviewDifficulty difficulty, QuestionMode questionMode,
            int durationMinutes, int questionCount, int passingPercentage,
            QuestionComposition composition, UUID knowledgeCollectionId) {
        if (passingPercentage < 1 || passingPercentage > 100) {
            throw new IllegalArgumentException("Passing percentage must be between 1 and 100");
        }
        if (composition.total() != questionCount) {
            throw new IllegalArgumentException(
                    "Question composition total must equal question count");
        }
        if ((questionMode == QuestionMode.RAG) != (knowledgeCollectionId != null)) {
            throw new IllegalArgumentException(
                    "RAG mode requires one knowledge collection; other modes cannot select one");
        }
        var definition = new InterviewDefinition();
        definition.id = UUID.randomUUID();
        definition.ownerSubject = ownerSubject;
        definition.title = title;
        definition.description = description;
        definition.skills = new ArrayList<>(skills);
        definition.difficulty = difficulty;
        definition.questionMode = questionMode;
        definition.durationMinutes = durationMinutes;
        definition.questionCount = questionCount;
        definition.mcqSingleCount = composition.mcqSingle();
        definition.mcqMultipleCount = composition.mcqMultiple();
        definition.shortTextCount = composition.shortText();
        definition.longTextCount = composition.longText();
        definition.passingPercentage = passingPercentage;
        definition.knowledgeCollectionId = knowledgeCollectionId;
        definition.status = InterviewStatus.DRAFT;
        definition.createdAt = Instant.now();
        definition.updatedAt = definition.createdAt;
        return definition;
    }

    public void archive() {
        status = InterviewStatus.ARCHIVED;
    }

    public void publish() {
        if (status != InterviewStatus.DRAFT) {
            throw new IllegalStateException("Only a draft interview can be published");
        }
        status = InterviewStatus.PUBLISHED;
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getOwnerSubject() { return ownerSubject; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public List<String> getSkills() { return List.copyOf(skills); }
    public InterviewDifficulty getDifficulty() { return difficulty; }
    public QuestionMode getQuestionMode() { return questionMode; }
    public int getDurationMinutes() { return durationMinutes; }
    public int getQuestionCount() { return questionCount; }
    public QuestionComposition getQuestionComposition() {
        return new QuestionComposition(
                mcqSingleCount, mcqMultipleCount, shortTextCount, longTextCount);
    }
    public int getPassingPercentage() { return passingPercentage; }
    public UUID getKnowledgeCollectionId() { return knowledgeCollectionId; }
    public InterviewStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
