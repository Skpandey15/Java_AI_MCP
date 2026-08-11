package com.onlineinterview.mcp.application;

import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.mcp")
public class McpAuthorizationProperties {
    /**
     * Committed default used only for local development. Shared and production environments
     * must override it; {@link McpAuthorizationSecretGuard} refuses to start when they don't.
     */
    public static final String LOCAL_DEVELOPMENT_SECRET =
            "local-mcp-authorization-secret-change-me";

    @Size(min = 32)
    private String authorizationSecret = LOCAL_DEVELOPMENT_SECRET;

    public String getAuthorizationSecret() { return authorizationSecret; }
    public void setAuthorizationSecret(String value) { authorizationSecret = value; }
}
