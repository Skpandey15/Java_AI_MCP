import { Navigate } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthProvider'

export function ProtectedRoute({ role, children }: { role: string | string[]; children: ReactNode }) {
  const auth = useAuth()
  if (!auth.initialized) return <p>Loading authentication…</p>
  if (!auth.authenticated) return <Navigate to="/" replace />
  const allowed = Array.isArray(role) ? role : [role]
  if (!allowed.some((r) => auth.roles.includes(r))) return <Navigate to="/unauthorized" replace />
  return children
}
