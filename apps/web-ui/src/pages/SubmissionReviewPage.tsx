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
  const [loadError, setLoadError] = useState('')
  const [scoreNotices, setScoreNotices] = useState<Record<string, { kind: 'success' | 'error'; text: string }>>({})
  const [finalizeNotice, setFinalizeNotice] = useState<{ kind: 'success' | 'error'; text: string }>()

  useEffect(() => {
    interviewApi.submission(sessionId).then((loaded) => {
      setSubmission(loaded)
      setScores(Object.fromEntries(loaded.questions.filter((q) => q.answerId)
        .map((q) => [q.answerId!, q.awardedScore ?? 0])))
      setFeedback(Object.fromEntries(loaded.questions.filter((q) => q.answerId)
        .map((q) => [q.answerId!, q.feedback ?? ''])))
      setOverallFeedback(loaded.feedback ?? '')
    }).catch((error: unknown) => setLoadError(error instanceof Error ? error.message : 'Unable to load submission'))
  }, [sessionId])

  async function save(answerId: string) {
    const question = submission?.questions.find((item) => item.answerId === answerId)
    const score = scores[answerId]
    if (!question || !Number.isInteger(score) || score < 0 || score > question.maxScore) {
      setScoreNotices((current) => ({
        ...current,
        [answerId]: {
          kind: 'error',
          text: `Enter a whole-number score between 0 and ${question?.maxScore ?? 0}.`,
        },
      }))
      return
    }

    setScoreNotices((current) => {
      const next = { ...current }
      delete next[answerId]
      return next
    })
    try {
      const updated = await interviewApi.scoreAnswer(
        sessionId, answerId, score, feedback[answerId] ?? '')
      setSubmission(updated)
      setScoreNotices((current) => ({
        ...current,
        [answerId]: { kind: 'success', text: 'Answer score saved.' },
      }))
    } catch (error) {
      setScoreNotices((current) => ({
        ...current,
        [answerId]: {
          kind: 'error',
          text: error instanceof Error ? error.message : 'Unable to save score',
        },
      }))
    }
  }

  async function finalize() {
    if (!window.confirm('Finalize this review? Scores will become visible to the candidate.')) return
    setFinalizeNotice(undefined)
    try {
      setSubmission(await interviewApi.finalizeReview(sessionId, overallFeedback))
      setFinalizeNotice({ kind: 'success', text: 'Review finalized and released to the candidate.' })
    } catch (error) {
      setFinalizeNotice({
        kind: 'error',
        text: error instanceof Error ? error.message : 'Unable to finalize review',
      })
    }
  }

  if (!submission) return <main className="dashboard">
    <p className={loadError ? 'error-message' : undefined} role={loadError ? 'alert' : undefined}>
      {loadError || 'Loading submission…'}
    </p>
  </main>
  const pending = submission.reviewStatus === 'PENDING_REVIEW'

  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Submission review</p><h1>{submission.interviewTitle}</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer/submissions')}>Review queue</button>
    </div>
    <p><strong>Candidate:</strong> {submission.candidateName} — {submission.candidateEmail}</p>
    <p><strong>Status:</strong> {submission.reviewStatus.replaceAll('_', ' ')}</p>
    <p><strong>Objective score:</strong> {submission.objectiveScore} / {submission.maxScore}</p>
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
            {scoreNotices[question.answerId] && <p
              className={scoreNotices[question.answerId].kind === 'error' ? 'error-message' : 'status-message'}
              role={scoreNotices[question.answerId].kind === 'error' ? 'alert' : 'status'}>
              {scoreNotices[question.answerId].text}
            </p>}
          </div>
          : <p><strong>Score:</strong> 0 / {question.maxScore} (unanswered)</p>}
    </section>)}
    <section className="review-summary">
      <label>Overall feedback<textarea disabled={!pending} value={overallFeedback}
        onChange={(e) => setOverallFeedback(e.target.value)} /></label>
      {pending
        ? <button onClick={() => void finalize()}>Finalize and release result</button>
        : <h2>Total score: {submission.totalScore} / {submission.maxScore}</h2>}
      {finalizeNotice && <p
        className={finalizeNotice.kind === 'error' ? 'error-message' : 'status-message'}
        role={finalizeNotice.kind === 'error' ? 'alert' : 'status'}>
        {finalizeNotice.text}
      </p>}
    </section>
  </main>
}
