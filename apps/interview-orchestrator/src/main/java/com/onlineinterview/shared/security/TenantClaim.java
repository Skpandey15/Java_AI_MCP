package com.onlineinterview.shared.security;

import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

public final class TenantClaim {
    public static final String CLAIM_NAME = "tenant_id";
    public static final String DEFAULT_TENANT = "default";
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_-]{0,79}");

    private TenantClaim() {}

    public static String required(Jwt jwt) {
        String tenant = jwt.getClaimAsString(CLAIM_NAME);
        if (tenant == null || !VALID.matcher(tenant).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "A valid tenant_id claim is required");
        }
        return tenant;
    }
}
