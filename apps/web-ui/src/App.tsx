import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { AuthProvider, useAuth } from './auth/AuthProvider'
import { ProtectedRoute } from './auth/ProtectedRoute'
import { CandidateDashboard } from './pages/CandidateDashboard'
import { InterviewerDashboard } from './pages/InterviewerDashboard'
import { InterviewSessionPage } from './pages/InterviewSessionPage'
import { InterviewerSubmissionsPage } from './pages/InterviewerSubmissionsPage'
import { SubmissionReviewPage } from './pages/SubmissionReviewPage'
import { CandidateResultPage } from './pages/CandidateResultPage'

export function dashboardPath(roles: string[]) {
  if (roles.includes('interviewer')) return '/interviewer'
  if (roles.includes('candidate')) return '/candidate'
  return '/unauthorized'
}

function LandingPage() {
  const auth = useAuth()

  if (!auth.initialized) {
    return <main className="shell"><p>Connecting to secure login…</p></main>
  }
  if (auth.authenticated) {
    return <Navigate to={dashboardPath(auth.roles)} replace />
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
          <Route path="/candidate/sessions/:sessionId/result" element={<ProtectedRoute role="candidate"><CandidateResultPage /></ProtectedRoute>} />
          <Route path="/interviewer" element={<ProtectedRoute role="interviewer"><InterviewerDashboard /></ProtectedRoute>} />
          <Route path="/interviewer/submissions" element={<ProtectedRoute role="interviewer"><InterviewerSubmissionsPage /></ProtectedRoute>} />
          <Route path="/interviewer/submissions/:sessionId" element={<ProtectedRoute role="interviewer"><SubmissionReviewPage /></ProtectedRoute>} />
          <Route path="/unauthorized" element={<main className="dashboard"><h1>Access denied</h1><p>Your account does not have permission for this dashboard.</p></main>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
