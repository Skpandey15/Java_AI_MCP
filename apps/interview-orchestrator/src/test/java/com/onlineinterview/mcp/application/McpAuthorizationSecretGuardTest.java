package com.onlineinterview.mcp.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class McpAuthorizationSecretGuardTest {

    @Test
    void rejectsCommittedDefaultSecretInProtectedEnvironments() {
        for (String profile : new String[] {"uat", "prod"}) {
            var environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
            var properties = new McpAuthorizationProperties();
            properties.setAuthorizationSecret(McpAuthorizationProperties.LOCAL_DEVELOPMENT_SECRET);

            assertThatThrownBy(() -> new McpAuthorizationSecretGuard(environment, properties))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("app.mcp.authorization-secret")
                    .hasMessageContaining("must be overridden outside local development");
        }
    }

    @Test
    void rejectsCommittedDefaultSecretWhenAProtectedProfileIsAmongSeveralActive() {
        // Environment.acceptsProfiles matches when ANY active profile is requested, so a
        // protected profile mixed with unprotected ones must still trip the guard.
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "local,prod");
        var properties = new McpAuthorizationProperties();
        properties.setAuthorizationSecret(McpAuthorizationProperties.LOCAL_DEVELOPMENT_SECRET);

        assertThatThrownBy(() -> new McpAuthorizationSecretGuard(environment, properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must be overridden outside local development");
    }

    @Test
    void allowsSuppliedSecretInProtectedEnvironments() {
        var environment = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        var properties = new McpAuthorizationProperties();
        properties.setAuthorizationSecret("a-real-32-byte-minimum-production-secret");

        assertThatCode(() -> new McpAuthorizationSecretGuard(environment, properties))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsCommittedDefaultSecretInLocalAndDevEnvironments() {
        for (String profile : new String[] {"local", "dev"}) {
            var environment = new MockEnvironment().withProperty("spring.profiles.active", profile);
            var properties = new McpAuthorizationProperties();
            properties.setAuthorizationSecret(McpAuthorizationProperties.LOCAL_DEVELOPMENT_SECRET);

            assertThatCode(() -> new McpAuthorizationSecretGuard(environment, properties))
                    .doesNotThrowAnyException();
        }
    }
}
