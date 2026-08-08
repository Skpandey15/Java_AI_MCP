package com.onlineinterview.session.adaptive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** One asked question in an adaptive interview; the answer and score fill in when the
 *  candidate responds and the agent evaluates it on the following turn. */
@Entity
@Table(name = "adaptive_turn")
public class AdaptiveTurn {
    @Id
    private UUID id;

    @Column(name = "session_id", nullable = false)
    private UUID sessionId;

    @Column(nullable = false)
    private int ordinal;

    @Column(name = "question_id")
    private UUID questionId;

    @Column(name = "question_text", nullable = false, columnDefinition = "TEXT")
    private String questionText;

    @Column(nullable = false, length = 200)
    private String skill;

    @Column(nullable = false, length = 40)
    private String difficulty;

    @Column(nullable = false, length = 20)
    private String source;

    @Column(name = "answer_text", columnDefinition = "TEXT")
    private String answerText;

    @Column
    private Integer score;

    @Column
    private Integer confidence;

    @Column(name = "agent_rationale", columnDefinition = "TEXT")
    private String agentRationale;

    protected AdaptiveTurn() {}

    public static AdaptiveTurn ask(UUID sessionId, int ordinal, UUID questionId,
            String questionText, String skill, String difficulty, String source,
            String agentRationale) {
        var turn = new AdaptiveTurn();
        turn.id = UUID.randomUUID();
        turn.sessionId = sessionId;
        turn.ordinal = ordinal;
        turn.questionId = questionId;
        turn.questionText = questionText;
        turn.skill = skill;
        turn.difficulty = difficulty;
        turn.source = source;
        turn.agentRationale = agentRationale;
        return turn;
    }

    public void recordAnswer(String answer) {
        this.answerText = answer;
    }

    public void recordEvaluation(int score, int confidence) {
        this.score = score;
        this.confidence = confidence;
    }

    public boolean isAnswered() {
        return answerText != null;
    }

    public UUID getId() { return id; }
    public UUID getSessionId() { return sessionId; }
    public int getOrdinal() { return ordinal; }
    public UUID getQuestionId() { return questionId; }
    public String getQuestionText() { return questionText; }
    public String getSkill() { return skill; }
    public String getDifficulty() { return difficulty; }
    public String getSource() { return source; }
    public String getAnswerText() { return answerText; }
    public Integer getScore() { return score; }
    public Integer getConfidence() { return confidence; }
    public String getAgentRationale() { return agentRationale; }
}
