package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onlineinterview.mcp.domain.McpActorRole;
import com.onlineinterview.mcp.domain.McpWorkflow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class McpAuthorizationTokenServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-30T10:00:00Z");

    @Test
    void signsAndVerifiesBoundShortLivedContext() {
        var service = service(NOW);
        var context = context(NOW.minusSeconds(1), NOW.plusSeconds(60));

        String token = service.issue(context);

        assertThat(service.verify(token)).isEqualTo(context);
        assertThat(token).contains(".");
    }

    @Test
    void rejectsTamperedMalformedExpiredAndFutureTokens() {
        var service = service(NOW);
        String valid = service.issue(context(NOW, NOW.plusSeconds(60)));
        assertThatThrownBy(() -> service.verify(valid + "x"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify("invalid"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(
                service.issue(context(NOW.minusSeconds(60), NOW))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(
                service.issue(context(NOW.plusSeconds(6), NOW.plusSeconds(60)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static McpAuthorizationTokenService service(Instant now) {
        var properties = new McpAuthorizationProperties();
        properties.setAuthorizationSecret("01234567890123456789012345678901");
        return new McpAuthorizationTokenService(
                new ObjectMapper().findAndRegisterModules(), properties,
                Clock.fixed(now, ZoneOffset.UTC));
    }

    private static McpAuthorizationContext context(Instant issued, Instant expires) {
        return new McpAuthorizationContext(UUID.randomUUID(),
                McpWorkflow.QUESTION_GENERATION, "knowledge", "search_knowledge",
                "actor", McpActorRole.INTERVIEWER, "INTERVIEW", UUID.randomUUID(),
                5, false, issued, expires);
    }
}
