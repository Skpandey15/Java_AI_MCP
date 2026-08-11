package com.onlineinterview.mcp.application;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard that refuses to boot a shared or production deployment while the MCP
 * authorization secret is still the committed local-development default.
 *
 * <p>The internal MCP endpoint ({@code /internal/mcp/**}) is {@code permitAll()} at the Spring
 * Security layer and trusts this HMAC secret alone to authorize tool execution. A leaked or
 * defaulted secret would let any network-reachable client forge authorization tokens and drive
 * MCP tools, so the value must never fall back to the committed default outside local
 * development. This mirrors the AI service's {@code reject_local_credentials_outside_local}
 * check for the shared service token and provides defense-in-depth beyond the profile YAML,
 * which only guarantees the variable is <em>present</em>, not that it is not the known default.
 */
@Component
class McpAuthorizationSecretGuard {
    private static final Profiles PROTECTED_ENVIRONMENTS = Profiles.of("uat", "prod");

    McpAuthorizationSecretGuard(Environment environment, McpAuthorizationProperties properties) {
        if (environment.acceptsProfiles(PROTECTED_ENVIRONMENTS)
                && McpAuthorizationProperties.LOCAL_DEVELOPMENT_SECRET
                        .equals(properties.getAuthorizationSecret())) {
            throw new IllegalStateException(
                    "MCP_AUTHORIZATION_SECRET must be supplied outside local development");
        }
    }
}
