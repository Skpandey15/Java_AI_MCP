import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { interviewApi, type Assignment } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

function candidateStatus(assignment: Assignment) {
  if (assignment.reviewStatus === 'REVIEWED') return 'REVIEWED — RESULT AVAILABLE'
  if (assignment.sessionState === 'SUBMITTED') return 'AWAITING REVIEW'
  if (assignment.sessionState === 'IN_PROGRESS') return 'IN PROGRESS'
  if (assignment.sessionState === 'EXPIRED') return 'EXPIRED'
  return assignment.status
}

export function CandidateDashboard() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [starting, setStarting] = useState('')
  const [assignments, setAssignments] = useState<Assignment[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    async function load() {
      try {
        await interviewApi.completeCandidateProfile()
        setAssignments(await interviewApi.candidateAssignments())
      } catch {
        setError('Unable to complete your candidate profile or load interviews. Please try again.')
      }
    }
    void load()
  }, [])

  async function start(assignment: Assignment) {
    if (assignment.questionMode === 'ADAPTIVE') {
      navigate(`/candidate/adaptive/${assignment.id}`)
      return
    }
    setStarting(assignment.id)
    try {
      const session = await interviewApi.startSession(assignment.id)
      navigate(`/candidate/sessions/${session.id}`)
    } catch {
      setError('This interview cannot be started outside its scheduled window.')
      setStarting('')
    }
  }

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
            <p><strong>Status:</strong> {candidateStatus(assignment)}</p>
            <p><strong>Starts:</strong> {new Date(assignment.startsAt).toLocaleString()}</p>
            <p><strong>Ends:</strong> {new Date(assignment.endsAt).toLocaleString()}</p>
            <p><strong>Maximum attempts:</strong> {assignment.maxAttempts}</p>
            {assignment.sessionState === 'SUBMITTED'
              ? assignment.reviewStatus === 'REVIEWED' && assignment.sessionId
                ? <button onClick={() => navigate(`/candidate/sessions/${assignment.sessionId}/result`)}>
                  View result
                </button>
                : <button disabled>Awaiting review</button>
              : <button disabled={starting === assignment.id} onClick={() => void start(assignment)}>
                {starting === assignment.id
                  ? 'Starting…'
                  : assignment.sessionState === 'IN_PROGRESS' ? 'Resume interview' : 'Start interview'}
              </button>}
          </article>
        ))}
      </div>
    </main>
  )
}
