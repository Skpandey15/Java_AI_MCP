import { useAuth } from '../auth/AuthProvider'

export function CandidateDashboard() {
  const auth = useAuth()
  return <main className="dashboard"><h1>Candidate dashboard</h1><p>Your upcoming interviews will appear here.</p><button onClick={auth.logout}>Sign out</button></main>
}
