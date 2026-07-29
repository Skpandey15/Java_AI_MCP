const runtimeConfig = window.__ONLINE_INTERVIEW_CONFIG__ ?? {}

export const appConfig = {
  apiBaseUrl: runtimeConfig.apiBaseUrl ?? import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080',
  keycloakUrl: runtimeConfig.keycloakUrl ?? import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8090',
  keycloakRealm: runtimeConfig.keycloakRealm ?? import.meta.env.VITE_KEYCLOAK_REALM ?? 'online-interview',
  keycloakClientId:
    runtimeConfig.keycloakClientId
    ?? import.meta.env.VITE_KEYCLOAK_CLIENT_ID
    ?? 'online-interview-web',
}
