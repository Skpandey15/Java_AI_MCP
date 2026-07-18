import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { keycloak } from './keycloak'

type AuthContextValue = {
  initialized: boolean
  authenticated: boolean
  roles: string[]
  token?: string
  login: () => void
  register: () => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [initialized, setInitialized] = useState(false)
  const [authenticated, setAuthenticated] = useState(false)

  useEffect(() => {
    keycloak
      .init({ onLoad: 'check-sso', pkceMethod: 'S256', checkLoginIframe: false })
      .then((isAuthenticated) => {
        setAuthenticated(isAuthenticated)
        setInitialized(true)
      })
      .catch(() => setInitialized(true))
  }, [])

  const value = useMemo<AuthContextValue>(() => ({
    initialized,
    authenticated,
    roles: keycloak.realmAccess?.roles ?? [],
    token: keycloak.token,
    login: () => void keycloak.login(),
    register: () => void keycloak.register(),
    logout: () => void keycloak.logout({ redirectUri: window.location.origin }),
  }), [authenticated, initialized])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}
