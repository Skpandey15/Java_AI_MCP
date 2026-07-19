import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { interviewApi, type SubmissionSummary } from '../api/interviewApi'

export function InterviewerSubmissionsPage() {
  const navigate = useNavigate()
  const [submissions, setSubmissions] = useState<SubmissionSummary[]>([])
  const [error, setError] = useState('')
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    setError('')
    interviewApi.submissions(page).then((result) => {
      setSubmissions(result.content)
      setTotalPages(result.totalPages)
    }).catch((value: unknown) =>
      setError(value instanceof Error ? value.message : 'Unable to load submissions'))
      .finally(() => setLoading(false))
  }, [page])

  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Interviewer workspace</p><h1>Submission reviews</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer')}>Interview management</button>
    </div>
    {error && <p className="error-message">{error}</p>}
    {loading && <p>Loading submissions…</p>}
    {!loading && !error && submissions.length === 0 && <p>No submitted interviews were found.</p>}
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
    {totalPages > 1 && <nav className="pagination" aria-label="Submission pages">
      <button className="secondary-button" disabled={loading || page === 0}
        onClick={() => setPage((current) => current - 1)}>Previous</button>
      <span>Page {page + 1} of {totalPages}</span>
      <button className="secondary-button" disabled={loading || page + 1 >= totalPages}
        onClick={() => setPage((current) => current + 1)}>Next</button>
    </nav>}
  </main>
}
