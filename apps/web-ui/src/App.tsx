import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { CandidateDashboard } from './pages/CandidateDashboard'
import { InterviewerDashboard } from './pages/InterviewerDashboard'
import { InterviewSessionPage } from './pages/InterviewSessionPage'

function LandingPage() {
  const auth = useAuth()

  if (!auth.initialized) {
    return <main className="shell"><p>Connecting to secure login…</p></main>
  }
  if (auth.authenticated) {
    if (auth.roles.includes('interviewer')) return <Navigate to="/interviewer" replace />
    if (auth.roles.includes('candidate')) return <Navigate to="/candidate" replace />
    return <Navigate to="/unauthorized" replace />
  }

  return (
    <main className="shell">
      <section className="hero" aria-labelledby="page-title">
        <p className="eyebrow">Java + AI ecosystem</p>
        <h1 id="page-title">Online Interview</h1>
        <p className="summary">
          A secure platform for interviewers to schedule AI-assisted interviews
          and for candidates to complete their assigned sessions.
        </p>
        <div className="actions">
          <button type="button" onClick={auth.login}>Login</button>
          <button className="secondary" type="button" onClick={auth.register}>Candidate registration</button>
        </div>
        <p className="status">Phase 2C interview workflow is running.</p>
      </section>
    </main>
  )
}

export function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/candidate" element={<ProtectedRoute role="candidate"><CandidateDashboard /></ProtectedRoute>} />
          <Route path="/candidate/sessions/:sessionId" element={<ProtectedRoute role="candidate"><InterviewSessionPage /></ProtectedRoute>} />
          <Route path="/interviewer" element={<ProtectedRoute role="interviewer"><InterviewerDashboard /></ProtectedRoute>} />
          <Route path="/unauthorized" element={<main className="dashboard"><h1>Access denied</h1><p>Your account does not have permission for this dashboard.</p></main>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
