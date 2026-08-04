package com.onlineinterview.profile.provisioning;

import java.net.URI;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@Component
public class KeycloakIdentityAdminClient {
    private final RestClient rest;
    private final String baseUrl;
    private final String realm;
    private final String clientId;
    private final String clientSecret;

    public KeycloakIdentityAdminClient(RestClient.Builder builder,
            @Value("${app.identity.keycloak-admin.base-url:}") String baseUrl,
            @Value("${app.identity.keycloak-admin.realm:online-interview}") String realm,
            @Value("${app.identity.keycloak-admin.client-id:}") String clientId,
            @Value("${app.identity.keycloak-admin.client-secret:}") String clientSecret) {
        this.rest = builder.build();
        this.baseUrl = stripTrailingSlash(baseUrl);
        this.realm = realm;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String ensureInterviewer(String email, String displayName, String tenantId) {
        requireConfigured();
        String token = accessToken();
        Map<String, Object> user = findByEmail(token, email).orElseGet(() -> createUser(
                token, email, displayName, tenantId));
        validateTenant(user, tenantId);
        String id = Objects.toString(user.get("id"));
        updateUser(token, id, user, displayName, tenantId);
        assignRealmRole(token, id, "interviewer");
        return id;
    }

    public boolean hasExpectedIdentity(String subject, String email, String tenantId) {
        try {
            String token = accessToken();
            var user = getUser(token, subject);
            return Boolean.TRUE.equals(user.get("enabled"))
                    && email.equalsIgnoreCase(Objects.toString(user.get("email"), ""))
                    && tenantId.equals(firstAttribute(user, "tenant_id"))
                    && hasRealmRole(token, subject, "interviewer");
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private String accessToken() {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials"); form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        var response = rest.post().uri(baseUrl + "/realms/" + realm + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().body(Map.class);
        Object token = response == null ? null : response.get("access_token");
        if (!(token instanceof String value) || value.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Identity provider token unavailable");
        }
        return value;
    }

    private Optional<Map<String, Object>> findByEmail(String token, String email) {
        List<?> users = rest.get().uri(builder -> builder.path(baseUrl + "/admin/realms/" + realm + "/users")
                .queryParam("email", email).queryParam("exact", true).build())
                .headers(h -> h.setBearerAuth(token)).retrieve().body(List.class);
        if (users == null || users.isEmpty()) return Optional.empty();
        if (users.size() != 1) throw new ResponseStatusException(CONFLICT, "Email resolves to multiple identities");
        return Optional.of(castMap(users.getFirst()));
    }

    private Map<String, Object> createUser(String token, String email, String displayName, String tenantId) {
        var parts = displayName.strip().split("\\s+", 2);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", email); body.put("email", email); body.put("enabled", true);
        body.put("emailVerified", false); body.put("firstName", parts[0]);
        if (parts.length > 1) body.put("lastName", parts[1]);
        body.put("attributes", Map.of("tenant_id", List.of(tenantId)));
        var response = rest.post().uri(baseUrl + "/admin/realms/" + realm + "/users")
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().toBodilessEntity();
        URI location = response.getHeaders().getLocation();
        if (location == null) throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "Identity provider did not return the created identity");
        return getUser(token, location.getPath().substring(location.getPath().lastIndexOf('/') + 1));
    }

    private Map<String, Object> getUser(String token, String id) {
        Map<?, ?> user = rest.get().uri(baseUrl + "/admin/realms/" + realm + "/users/" + id)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
        if (user == null) throw new ResponseStatusException(NOT_FOUND, "Identity does not exist");
        return castMap(user);
    }

    private void updateUser(String token, String id, Map<String, Object> user,
            String displayName, String tenantId) {
        var copy = new LinkedHashMap<>(user);
        copy.put("enabled", true); copy.put("attributes", Map.of("tenant_id", List.of(tenantId)));
        var parts = displayName.strip().split("\\s+", 2); copy.put("firstName", parts[0]);
        copy.put("lastName", parts.length > 1 ? parts[1] : "");
        rest.put().uri(baseUrl + "/admin/realms/" + realm + "/users/" + id)
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(copy).retrieve().toBodilessEntity();
    }

    private void assignRealmRole(String token, String userId, String roleName) {
        Map<?, ?> role = rest.get().uri(baseUrl + "/admin/realms/" + realm + "/roles/" + roleName)
                .headers(h -> h.setBearerAuth(token)).retrieve().body(Map.class);
        if (role == null) throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Required identity role is missing");
        rest.post().uri(baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm")
                .headers(h -> h.setBearerAuth(token)).contentType(MediaType.APPLICATION_JSON)
                .body(List.of(role)).retrieve().toBodilessEntity();
    }

    private boolean hasRealmRole(String token, String userId, String roleName) {
        List<?> roles = rest.get().uri(baseUrl + "/admin/realms/" + realm + "/users/"
                        + userId + "/role-mappings/realm/composite")
                .headers(h -> h.setBearerAuth(token)).retrieve().body(List.class);
        return roles != null && roles.stream().filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(role -> roleName.equals(role.get("name")));
    }

    private void validateTenant(Map<String, Object> user, String requestedTenant) {
        String existing = firstAttribute(user, "tenant_id");
        if (existing != null && !existing.equals(requestedTenant)) {
            throw new ResponseStatusException(CONFLICT, "Identity belongs to another tenant");
        }
    }

    private static String firstAttribute(Map<String, Object> user, String name) {
        Object attributes = user.get("attributes");
        if (!(attributes instanceof Map<?, ?> map)) return null;
        Object values = map.get(name);
        if (values instanceof List<?> list && !list.isEmpty()) return Objects.toString(list.getFirst(), null);
        return values == null ? null : Objects.toString(values);
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }
    private void requireConfigured() {
        if (baseUrl.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "Interviewer identity provisioning is not configured");
        }
    }
    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }
}
