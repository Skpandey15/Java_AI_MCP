package com.onlineinterview.session.adaptive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Adaptive AI Interviewer settings. Disabled by default — the flag is also the kill-switch. */
@Component
@ConfigurationProperties(prefix = "app.adaptive")
public class AdaptiveProperties {
    private boolean enabled;
    private int maxTurns = 12;
    private int tokenBudget = 60000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }

    public int getMaxTurns() { return maxTurns; }
    public void setMaxTurns(int value) { maxTurns = value; }

    public int getTokenBudget() { return tokenBudget; }
    public void setTokenBudget(int value) { tokenBudget = value; }
}
