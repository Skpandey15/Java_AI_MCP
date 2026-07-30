package com.onlineinterview.mcp.application;

import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "app.mcp")
public class McpAuthorizationProperties {
    @Size(min = 32)
    private String authorizationSecret =
            "local-mcp-authorization-secret-change-me";

    public String getAuthorizationSecret() { return authorizationSecret; }
    public void setAuthorizationSecret(String value) { authorizationSecret = value; }
}
