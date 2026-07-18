import { useEffect, useState } from 'react'
import { interviewApi, type Assignment } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

export function CandidateDashboard() {
  const auth = useAuth()
  const [assignments, setAssignments] = useState<Assignment[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    interviewApi.candidateAssignments()
      .then(setAssignments)
      .catch(() => setError('Unable to load interviews. Please try again.'))
  }, [])

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Candidate workspace</p><h1>My interviews</h1></div>
        <button className="secondary-button" onClick={auth.logout}>Sign out</button>
      </div>
      {error && <p className="error">{error}</p>}
      {!error && assignments.length === 0 && <p>No interviews are currently assigned.</p>}
      <div className="card-grid">
        {assignments.map((assignment) => (
          <article className="card" key={assignment.id}>
            <h2>{assignment.interviewTitle}</h2>
            <p><strong>Status:</strong> {assignment.status}</p>
            <p><strong>Starts:</strong> {new Date(assignment.startsAt).toLocaleString()}</p>
            <p><strong>Ends:</strong> {new Date(assignment.endsAt).toLocaleString()}</p>
            <p><strong>Maximum attempts:</strong> {assignment.maxAttempts}</p>
          </article>
        ))}
      </div>
    </main>
  )
}
