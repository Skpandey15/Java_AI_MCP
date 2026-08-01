package com.onlineinterview.common.ratelimit;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Per-principal, per-minute request limits for abuse- and cost-sensitive endpoints
 * (session lifecycle, AI generation and knowledge ingestion). Limits are enforced
 * per orchestrator instance; see {@link RateLimitInterceptor} for the trade-off note.
 *
 * <p>Registered via {@code @EnableConfigurationProperties} on {@link RateLimitWebConfig}
 * (not as a {@code @Component}) so it is available inside {@code @WebMvcTest} slices.
 */
@Validated
@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {
    private boolean enabled = true;
    @Min(1) @Max(10_000) private int sessionStartPerMinute = 30;
    @Min(1) @Max(10_000) private int answerAutosavePerMinute = 240;
    @Min(1) @Max(10_000) private int sessionSubmitPerMinute = 30;
    @Min(1) @Max(10_000) private int aiGenerationPerMinute = 20;
    @Min(1) @Max(10_000) private int knowledgeMutationPerMinute = 20;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean value) { enabled = value; }
    public int getSessionStartPerMinute() { return sessionStartPerMinute; }
    public void setSessionStartPerMinute(int value) { sessionStartPerMinute = value; }
    public int getAnswerAutosavePerMinute() { return answerAutosavePerMinute; }
    public void setAnswerAutosavePerMinute(int value) { answerAutosavePerMinute = value; }
    public int getSessionSubmitPerMinute() { return sessionSubmitPerMinute; }
    public void setSessionSubmitPerMinute(int value) { sessionSubmitPerMinute = value; }
    public int getAiGenerationPerMinute() { return aiGenerationPerMinute; }
    public void setAiGenerationPerMinute(int value) { aiGenerationPerMinute = value; }
    public int getKnowledgeMutationPerMinute() { return knowledgeMutationPerMinute; }
    public void setKnowledgeMutationPerMinute(int value) { knowledgeMutationPerMinute = value; }
}
