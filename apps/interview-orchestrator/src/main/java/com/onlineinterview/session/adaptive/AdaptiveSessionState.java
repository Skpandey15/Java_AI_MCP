package com.onlineinterview.session.adaptive;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** Per-session adaptive budget/phase, 1:1 with an interview_session in ADAPTIVE mode. */
@Entity
@Table(name = "adaptive_session_state")
public class AdaptiveSessionState {
    @Id
    @Column(name = "session_id")
    private UUID sessionId;

    @Column(name = "turns_used", nullable = false)
    private int turnsUsed;

    @Column(name = "max_turns", nullable = false)
    private int maxTurns;

    @Column(name = "tokens_used", nullable = false)
    private int tokensUsed;

    @Column(name = "token_budget", nullable = false)
    private int tokenBudget;

    @Column(nullable = false, length = 20)
    private String phase;

    protected AdaptiveSessionState() {}

    public static AdaptiveSessionState create(UUID sessionId, int maxTurns, int tokenBudget) {
        var state = new AdaptiveSessionState();
        state.sessionId = sessionId;
        state.turnsUsed = 0;
        state.maxTurns = maxTurns;
        state.tokensUsed = 0;
        state.tokenBudget = tokenBudget;
        state.phase = "RUNNING";
        return state;
    }

    public void recordAsk(int tokens) {
        turnsUsed++;
        tokensUsed += Math.max(0, tokens);
    }

    public void conclude(int tokens) {
        tokensUsed += Math.max(0, tokens);
        phase = "DONE";
    }

    public int turnsRemaining() {
        return Math.max(0, maxTurns - turnsUsed);
    }

    public boolean isDone() {
        return "DONE".equals(phase);
    }

    public UUID getSessionId() { return sessionId; }
    public int getTurnsUsed() { return turnsUsed; }
    public int getMaxTurns() { return maxTurns; }
    public int getTokensUsed() { return tokensUsed; }
    public int getTokenBudget() { return tokenBudget; }
    public String getPhase() { return phase; }
}
