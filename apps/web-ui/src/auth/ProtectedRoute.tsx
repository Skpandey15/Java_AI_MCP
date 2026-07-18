import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthProvider'

export function ProtectedRoute({ role, children }: { role: string; children: ReactNode }) {
  const auth = useAuth()
  if (!auth.initialized) return <p>Loading authentication…</p>
  if (!auth.authenticated) return <Navigate to="/" replace />
  if (!auth.roles.includes(role)) return <Navigate to="/unauthorized" replace />
  return children
}
