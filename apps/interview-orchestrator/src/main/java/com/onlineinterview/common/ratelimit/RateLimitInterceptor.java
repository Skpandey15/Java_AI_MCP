package com.onlineinterview.common.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntSupplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Fixed-window (per calendar minute) request limiter keyed by authenticated subject and
 * logical bucket. It protects the abuse- and cost-sensitive endpoints called out in the
 * security design (session start/answer/submit, AI generation, knowledge ingestion).
 *
 * <p>Counters are held in-process, so limits are enforced per orchestrator instance. This
 * matches the existing per-instance MCP call limiter and is sufficient as a first layer;
 * a Redis-backed shared window can replace the store later without changing the rule set.
 */
public class RateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitProperties properties;
    private final AntPathMatcher matcher = new AntPathMatcher();
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final List<Rule> rules;

    public RateLimitInterceptor(RateLimitProperties properties) {
        this.properties = properties;
        this.rules = List.of(
                new Rule("POST", "/api/v1/candidate/assignments/*/sessions",
                        "session-start", properties::getSessionStartPerMinute),
                new Rule("PUT", "/api/v1/candidate/sessions/*/answers/*",
                        "answer-autosave", properties::getAnswerAutosavePerMinute),
                new Rule("POST", "/api/v1/candidate/sessions/*/submit",
                        "session-submit", properties::getSessionSubmitPerMinute),
                new Rule("POST", "/api/v1/interviews/*/questions:generate",
                        "ai-generation", properties::getAiGenerationPerMinute),
                new Rule("POST", "/api/v1/interviews/*/questions:compose",
                        "ai-generation", properties::getAiGenerationPerMinute),
                new Rule("POST", "/api/v1/knowledge/collections/*/documents",
                        "knowledge-mutation", properties::getKnowledgeMutationPerMinute),
                new Rule("POST", "/api/v1/knowledge/documents/*:prepare",
                        "knowledge-mutation", properties::getKnowledgeMutationPerMinute),
                new Rule("POST", "/api/v1/knowledge/documents/*:ingest",
                        "knowledge-mutation", properties::getKnowledgeMutationPerMinute));
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!properties.isEnabled()) {
            return true;
        }
        String method = request.getMethod();
        String path = request.getRequestURI();
        Rule rule = null;
        for (Rule candidate : rules) {
            if (candidate.method().equals(method) && matcher.match(candidate.pattern(), path)) {
                rule = candidate;
                break;
            }
        }
        if (rule == null) {
            return true;
        }
        int limit = rule.limit().getAsInt();
        String key = rule.bucket() + "|" + principal(request);
        long minute = Instant.now().getEpochSecond() / 60;
        Window window = windows.computeIfAbsent(key, ignored -> new Window());
        synchronized (window) {
            if (window.minute != minute) {
                window.minute = minute;
                window.count = 0;
            }
            if (window.count >= limit) {
                response.setHeader("Retry-After", "60");
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "Rate limit exceeded for " + rule.bucket());
            }
            window.count++;
        }
        return true;
    }

    private String principal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getName() != null) {
            return authentication.getName();
        }
        return "ip:" + request.getRemoteAddr();
    }

    private static final class Window {
        private long minute = -1;
        private int count;
    }

    private record Rule(String method, String pattern, String bucket, IntSupplier limit) {}
}
