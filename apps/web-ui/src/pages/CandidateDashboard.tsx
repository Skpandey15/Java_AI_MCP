import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { interviewApi, type Assignment } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

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

  async function start(assignmentId: string) {
    setStarting(assignmentId)
    try {
      const session = await interviewApi.startSession(assignmentId)
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
            <p><strong>Status:</strong> {assignment.status}</p>
            <p><strong>Starts:</strong> {new Date(assignment.startsAt).toLocaleString()}</p>
            <p><strong>Ends:</strong> {new Date(assignment.endsAt).toLocaleString()}</p>
            <p><strong>Maximum attempts:</strong> {assignment.maxAttempts}</p>
            <button disabled={starting === assignment.id} onClick={() => void start(assignment.id)}>{starting === assignment.id ? 'Starting…' : 'Start or resume'}</button>
          </article>
        ))}
      </div>
    </main>
  )
}
