package com.onlineinterview.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

class TenantClaimTest {
    @Test
    void readsValidTenantClaim() {
        assertThat(TenantClaim.required(jwt("tenant-1"))).isEqualTo("tenant-1");
    }

    @Test
    void rejectsMissingAndMalformedTenantClaims() {
        assertThatThrownBy(() -> TenantClaim.required(jwt(null)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
        assertThatThrownBy(() -> TenantClaim.required(jwt("../other")))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");
    }

    private static Jwt jwt(String tenant) {
        var claims = new java.util.HashMap<String, Object>();
        claims.put("sub", "subject");
        if (tenant != null) {
            claims.put("tenant_id", tenant);
        }
        return new Jwt("token", Instant.EPOCH, Instant.EPOCH.plusSeconds(60),
                Map.of("alg", "none"), claims);
    }
}
