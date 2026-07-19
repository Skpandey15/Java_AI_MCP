import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { interviewApi, type SubmissionDetail } from '../api/interviewApi'

export function SubmissionReviewPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()
  const [submission, setSubmission] = useState<SubmissionDetail>()
  const [scores, setScores] = useState<Record<string, number>>({})
  const [feedback, setFeedback] = useState<Record<string, string>>({})
  const [overallFeedback, setOverallFeedback] = useState('')
  const [message, setMessage] = useState('')

  useEffect(() => {
    interviewApi.submission(sessionId).then((loaded) => {
      setSubmission(loaded)
      setScores(Object.fromEntries(loaded.questions.filter((q) => q.answerId)
        .map((q) => [q.answerId!, q.awardedScore ?? 0])))
      setFeedback(Object.fromEntries(loaded.questions.filter((q) => q.answerId)
        .map((q) => [q.answerId!, q.feedback ?? ''])))
      setOverallFeedback(loaded.feedback ?? '')
    }).catch((error: unknown) => setMessage(error instanceof Error ? error.message : 'Unable to load submission'))
  }, [sessionId])

  async function save(answerId: string) {
    try {
      const updated = await interviewApi.scoreAnswer(
        sessionId, answerId, scores[answerId] ?? 0, feedback[answerId] ?? '')
      setSubmission(updated)
      setMessage('Answer score saved.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to save score')
    }
  }

  async function finalize() {
    if (!window.confirm('Finalize this review? Scores will become visible to the candidate.')) return
    try {
      setSubmission(await interviewApi.finalizeReview(sessionId, overallFeedback))
      setMessage('Review finalized and released to the candidate.')
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to finalize review')
    }
  }

  if (!submission) return <main className="dashboard"><p>{message || 'Loading submission…'}</p></main>
  const pending = submission.reviewStatus === 'PENDING_REVIEW'

  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Submission review</p><h1>{submission.interviewTitle}</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer/submissions')}>Review queue</button>
    </div>
    <p><strong>Candidate:</strong> {submission.candidateName} — {submission.candidateEmail}</p>
    <p><strong>Status:</strong> {submission.reviewStatus.replaceAll('_', ' ')}</p>
    <p><strong>Objective score:</strong> {submission.objectiveScore} / {submission.maxScore}</p>
    {message && <p className="status-message">{message}</p>}
    {submission.questions.map((question) => <section className="question-card" key={question.questionId}>
      <div className="question-meta"><span>{question.type.replaceAll('_', ' ')}</span><span>{question.maxScore} points</span></div>
      <h2>{question.order}. {question.prompt}</h2>
      {question.options.length > 0 && <p><strong>Correct:</strong> {question.correctAnswers.join(', ')}</p>}
      <div className="answer-panel"><strong>Candidate answer</strong><pre>{question.content || 'No answer submitted'}</pre></div>
      {question.autoScored
        ? <p><strong>Automatic score:</strong> {question.awardedScore ?? 0} / {question.maxScore}</p>
        : question.answerId
          ? <div className="review-fields">
            <label>Score<input disabled={!pending} type="number" min="0" max={question.maxScore}
              value={scores[question.answerId] ?? 0}
              onChange={(e) => setScores({...scores, [question.answerId!]: Number(e.target.value)})} /></label>
            <label>Feedback<textarea disabled={!pending} value={feedback[question.answerId] ?? ''}
              onChange={(e) => setFeedback({...feedback, [question.answerId!]: e.target.value})} /></label>
            {pending && <button onClick={() => void save(question.answerId!)}>Save score</button>}
          </div>
          : <p><strong>Score:</strong> 0 / {question.maxScore} (unanswered)</p>}
    </section>)}
    <section className="review-summary">
      <label>Overall feedback<textarea disabled={!pending} value={overallFeedback}
        onChange={(e) => setOverallFeedback(e.target.value)} /></label>
      {pending
        ? <button onClick={() => void finalize()}>Finalize and release result</button>
        : <h2>Total score: {submission.totalScore} / {submission.maxScore}</h2>}
    </section>
  </main>
}
