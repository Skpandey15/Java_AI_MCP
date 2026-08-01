import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { interviewApi, type CandidateResult } from '../api/interviewApi'

export function CandidateResultPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()
  const [result, setResult] = useState<CandidateResult>()
  const [error, setError] = useState('')

  useEffect(() => {
    interviewApi.candidateResult(sessionId).then(setResult)
      .catch((value: unknown) => setError(value instanceof Error ? value.message : 'Unable to load result'))
  }, [sessionId])

  if (!result) return <main className="dashboard"><p>{error || 'Loading result…'}</p></main>
  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Candidate result</p><h1>{result.interviewTitle}</h1></div>
      <button className="secondary-button" onClick={() => navigate('/candidate')}>Dashboard</button>
    </div>
    {result.reviewStatus !== 'REVIEWED'
      ? <section className="card"><h2>Review pending</h2><p>Your interviewer has not finalized this result yet.</p></section>
      : <>
        <section className={`result-outcome ${result.outcome === 'PASSED' ? 'result-passed' : 'result-not-selected'}`}>
          <span>Final outcome</span>
          <strong>{result.outcome === 'PASSED' ? 'PASSED' : 'NOT SELECTED'}</strong>
          <p>{result.percentage}% · Passing requirement {result.passingPercentage}%</p>
        </section>
        <section className="score-hero"><span>Total score</span><strong>{result.totalScore} / {result.maxScore}</strong>{result.feedback && <p>{result.feedback}</p>}</section>
        {result.coachingFeedback && <section className="card coaching-content-card">
          <h2>Your development plan</h2>
          <pre className="coaching-content">{result.coachingFeedback}</pre></section>}
        {result.answers.map((answer) => <section className="question-card" key={answer.order}>
          <div className="question-meta"><span>{answer.type.replaceAll('_', ' ')}</span><span>{answer.awardedScore ?? 0} / {answer.maxScore}</span></div>
          <h2>{answer.order}. {answer.prompt}</h2>
          <div className="answer-panel"><strong>Your answer</strong><pre>{answer.content || 'No answer submitted'}</pre></div>
          {answer.feedback && <p><strong>Reviewer feedback:</strong> {answer.feedback}</p>}
        </section>)}
      </>}
  </main>
}
