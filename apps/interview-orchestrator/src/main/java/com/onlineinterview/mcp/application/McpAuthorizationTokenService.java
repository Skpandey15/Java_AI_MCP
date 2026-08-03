package com.onlineinterview.mcp.application;

import tools.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

@Component
public class McpAuthorizationTokenService {
    private static final String HMAC = "HmacSHA256";
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final Clock clock;

    @Autowired
    public McpAuthorizationTokenService(
            ObjectMapper mapper, McpAuthorizationProperties properties) {
        this(mapper, properties, Clock.systemUTC());
    }

    McpAuthorizationTokenService(
            ObjectMapper mapper, McpAuthorizationProperties properties, Clock clock) {
        this.mapper = mapper;
        this.secret = properties.getAuthorizationSecret().getBytes(StandardCharsets.UTF_8);
        this.clock = clock;
    }

    public String issue(McpAuthorizationContext context) {
        try {
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mapper.writeValueAsBytes(context));
            return payload + "." + signature(payload);
        } catch (tools.jackson.core.JacksonException exception) {
            throw new IllegalArgumentException("Unable to serialize MCP authorization", exception);
        }
    }

    public McpAuthorizationContext verify(String token) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.", -1);
            if (parts.length != 2 || !MessageDigest.isEqual(
                    signature(parts[0]).getBytes(StandardCharsets.US_ASCII),
                    parts[1].getBytes(StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Invalid MCP authorization signature");
            }
            var context = mapper.readValue(Base64.getUrlDecoder().decode(parts[0]),
                    McpAuthorizationContext.class);
            Instant now = clock.instant();
            if (!context.expiresAt().isAfter(now) || context.issuedAt().isAfter(now.plusSeconds(5))) {
                throw new IllegalArgumentException("MCP authorization is expired or not yet valid");
            }
            return context;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid MCP authorization token", exception);
        }
    }

    private String signature(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(secret, HMAC));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.US_ASCII)));
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }
}
