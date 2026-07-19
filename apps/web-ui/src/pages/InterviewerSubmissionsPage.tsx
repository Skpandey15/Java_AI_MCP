import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { interviewApi, type SubmissionSummary } from '../api/interviewApi'

export function InterviewerSubmissionsPage() {
  const navigate = useNavigate()
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([])
  const [error, setError] = useState('')

  useEffect(() => {
    interviewApi.submissions().then(setSubmissions)
      .catch((value: unknown) => setError(value instanceof Error ? value.message : 'Unable to load submissions'))
  }, [])

  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Interviewer workspace</p><h1>Submission reviews</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer')}>Interview management</button>
    </div>
    {error && <p className="error-message">{error}</p>}
    {!error && submissions.length === 0 && <p>No submitted interviews are waiting for review.</p>}
    <div className="card-grid">
      {submissions.map((submission) => <article className="card" key={submission.sessionId}>
        <div className="card-heading"><h2>{submission.interviewTitle}</h2><span className="badge">{submission.reviewStatus.replaceAll('_', ' ')}</span></div>
        <p><strong>Candidate:</strong> {submission.candidateName} — {submission.candidateEmail}</p>
        <p><strong>Submitted:</strong> {new Date(submission.submittedAt).toLocaleString()}</p>
        <p><strong>Score:</strong> {submission.totalScore == null ? 'Pending' : `${submission.totalScore} / ${submission.maxScore} (${submission.percentage}%)`}</p>
        {submission.outcome && <p><strong>Outcome:</strong> {submission.outcome === 'PASSED' ? 'Passed' : 'Not selected'}</p>}
        <button onClick={() => navigate(`/interviewer/submissions/${submission.sessionId}`)}>
          {submission.reviewStatus === 'REVIEWED' ? 'View review' : 'Review submission'}
        </button>
      </article>)}
    </div>
  </main>
}
