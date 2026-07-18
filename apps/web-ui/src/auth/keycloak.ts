import Keycloak from 'keycloak-js'

export const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8090',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'online-interview',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'online-interview-web',
})
