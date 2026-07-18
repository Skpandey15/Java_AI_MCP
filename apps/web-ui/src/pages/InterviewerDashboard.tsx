import { useAuth } from '../auth/AuthProvider'

export function InterviewerDashboard() {
  const auth = useAuth()
  return <main className="dashboard"><h1>Interviewer dashboard</h1><p>Create and schedule interviews from this workspace.</p><button onClick={auth.logout}>Sign out</button></main>
}
