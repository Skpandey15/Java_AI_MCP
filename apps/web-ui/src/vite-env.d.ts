/// <reference types="vite/client" />

interface OnlineInterviewRuntimeConfig {
  apiBaseUrl?: string
  keycloakUrl?: string
  keycloakRealm?: string
  keycloakClientId?: string
}

interface Window {
  __ONLINE_INTERVIEW_CONFIG__?: OnlineInterviewRuntimeConfig
}
