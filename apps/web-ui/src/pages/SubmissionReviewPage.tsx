import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { interviewApi, type AnswerSuggestion, type CoachingResponse, type SubmissionDetail } from '../api/interviewApi'
import { Markdown } from '../components/Markdown'

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
  const [savingAnswer, setSavingAnswer] = useState('')
  const [finalizing, setFinalizing] = useState(false)
  const [suggestions, setSuggestions] = useState<Record<string, AnswerSuggestion>>({})
  const [coaching, setCoaching] = useState<CoachingResponse>()
  const [aiBusy, setAiBusy] = useState('')
  const [detailBusy, setDetailBusy] = useState('')

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

  useEffect(() => {
    if (!finalizeNotice) return
    const timer = window.setTimeout(() => setFinalizeNotice(undefined), 6000)
    return () => window.clearTimeout(timer)
  }, [finalizeNotice])

  useEffect(() => {
    if (submission?.reviewStatus !== 'REVIEWED') return
    interviewApi.currentCoaching(sessionId)
      .then(setCoaching)
      .catch((error: unknown) => setFinalizeNotice({
        kind: 'error',
        text: error instanceof Error ? error.message : 'Unable to load coaching feedback',
      }))
  }, [sessionId, submission?.reviewStatus])

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
    setSavingAnswer(answerId)
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
    } finally {
      setSavingAnswer('')
    }
  }

  async function finalize() {
    if (!window.confirm('Finalize this review? Scores will become visible to the candidate.')) return
    setFinalizeNotice(undefined)
    setFinalizing(true)
    try {
      setSubmission(await interviewApi.finalizeReview(sessionId, overallFeedback))
      setFinalizeNotice({ kind: 'success', text: 'Review finalized and released to the candidate.' })
    } catch (error) {
      setFinalizeNotice({
        kind: 'error',
        text: error instanceof Error ? error.message : 'Unable to finalize review',
      })
    } finally {
      setFinalizing(false)
    }
  }

  async function suggest() {
    setAiBusy('suggest')
    try {
      const list = await interviewApi.aiSuggest(sessionId)
      setSuggestions(Object.fromEntries(list.map((s) => [s.answerId, s])))
      setScores((current) => ({ ...current, ...Object.fromEntries(list.map((s) => [s.answerId, s.suggestedScore])) }))
    } catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'AI suggest failed' }) }
    finally { setAiBusy('') }
  }
  async function genCoaching() {
    setAiBusy('coach')
    try { setCoaching(await interviewApi.draftCoaching(sessionId)) }
    catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Coaching draft failed' }) }
    finally { setAiBusy('') }
  }
  async function releaseCoaching() {
    setAiBusy('approve')
    try { setCoaching(await interviewApi.approveCoaching(sessionId)) }
    catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Approve failed' }) }
    finally { setAiBusy('') }
  }
  async function genAnswerKey() {
    setAiBusy('answerKey')
    try { setSubmission(await interviewApi.generateAnswerKey(sessionId)) }
    catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Answer key generation failed' }) }
    finally { setAiBusy('') }
  }
  async function loadDetailedAnswer(answerId: string) {
    setDetailBusy(answerId)
    try { setSubmission(await interviewApi.explainAnswer(sessionId, answerId)) }
    catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Detailed answer failed' }) }
    finally { setDetailBusy('') }
  }
  async function emailResult() {
    setAiBusy('email')
    try {
      const { sentTo } = await interviewApi.emailResult(sessionId)
      setFinalizeNotice({ kind: 'success', text: `Result emailed to ${sentTo}.` })
    } catch (error) { setFinalizeNotice({ kind: 'error', text: error instanceof Error ? error.message : 'Email failed' }) }
    finally { setAiBusy('') }
  }

  if (!submission) return <main className="dashboard">
    <p className={loadError ? 'error-message' : undefined} role={loadError ? 'alert' : undefined}>
      {loadError || 'Loading submission…'}
    </p>
  </main>
  const pending = submission.reviewStatus === 'PENDING_REVIEW'
  const scoredQuestions = submission.questions.filter((question) =>
    question.autoScored || !question.answerId || question.awardedScore != null).length

  return <main className="dashboard">
    <div className="dashboard-header">
      <div><p className="eyebrow">Submission review</p><h1>{submission.interviewTitle}</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer/submissions')}>Review queue</button>
    </div>
    <p><strong>Candidate:</strong> {submission.candidateName} — {submission.candidateEmail}</p>
    <p><strong>Status:</strong> {submission.reviewStatus.replaceAll('_', ' ')}</p>
    <p><strong>Objective score:</strong> {submission.objectiveScore} / {submission.maxScore}</p>
    <p><strong>Passing requirement:</strong> {submission.passingPercentage}%</p>
    <p><strong>Review progress:</strong> {scoredQuestions} / {submission.questions.length} questions scored</p>
    <div className="compact-actions">
      {pending && <button className="secondary-button" disabled={aiBusy === 'suggest'} onClick={() => void suggest()}>
        {aiBusy === 'suggest' ? 'Scoring…' : '🤖 AI: suggest scores'}</button>}
      <button className="secondary-button" disabled={aiBusy === 'answerKey'} onClick={() => void genAnswerKey()}
        title="Generate a model answer (correct answer + why + example) for every question. Cached per question and shown to the candidate too.">
        {aiBusy === 'answerKey'
          ? 'Generating answer key…'
          : submission.questions.some((q) => q.modelAnswer)
            ? '🤖 Regenerate answer key'
            : '🤖 Generate answer key (details + examples)'}</button>
    </div>
    {submission.questions.map((question) => <section className="question-card" key={question.questionId}>
      <div className="question-meta"><span>{question.type.replaceAll('_', ' ')}</span><span>{question.maxScore} points</span></div>
      <h2>{question.order}. {question.prompt}</h2>
      {question.options.length > 0 && <p><strong>Correct:</strong> {question.correctAnswers.join(', ')}</p>}
      {question.citations?.length > 0 && <div className="citation-list">
        <strong>Sources</strong>
        {question.citations.map((citation) => <p key={citation.chunkId}>
          {citation.fileName} · section {citation.chunkIndex + 1}: {citation.excerpt}
        </p>)}
      </div>}
      <div className="answer-panel"><strong>Candidate answer</strong><pre>{question.content || 'No answer submitted'}</pre></div>
      {question.modelAnswer && <div className="model-answer">
        <strong>✓ Correct answer</strong>
        <Markdown className="model-answer-content" content={question.modelAnswer} />
      </div>}
      {question.detailedAnswer && <div className="model-answer detailed-answer">
        <strong>🔍 Detailed answer — why this answer fell short</strong>
        <Markdown className="model-answer-content" content={question.detailedAnswer} />
      </div>}
      {question.answerId && !question.detailedAnswer && (question.awardedScore ?? 0) < question.maxScore && <button
        className="secondary-button ai-detailed-answer" disabled={detailBusy === question.answerId}
        onClick={() => void loadDetailedAnswer(question.answerId!)}
        title="Ask the AI to explain, for this candidate's specific answer, why it is wrong and what the correct answer is.">
        {detailBusy === question.answerId ? 'Analyzing this answer…' : '🔍 Detailed answer'}</button>}
      {question.answerId && suggestions[question.answerId] && <p className="ai-suggestion">
        🤖 AI suggests {suggestions[question.answerId].suggestedScore}/{question.maxScore} (confidence{' '}
        {Math.round(suggestions[question.answerId].confidence * 100)}%): {suggestions[question.answerId].justification}</p>}
      {question.autoScored
        ? <p><strong>Automatic score:</strong> {question.awardedScore ?? 0} / {question.maxScore}</p>
        : question.answerId
          ? <div className="review-fields">
            <label>Score<input disabled={!pending} type="number" min="0" max={question.maxScore}
              value={scores[question.answerId] ?? 0}
              onChange={(e) => setScores({...scores, [question.answerId!]: Number(e.target.value)})} /></label>
            <label>Feedback<textarea disabled={!pending} value={feedback[question.answerId] ?? ''}
              onChange={(e) => setFeedback({...feedback, [question.answerId!]: e.target.value})} /></label>
            {pending && <button disabled={savingAnswer === question.answerId || finalizing}
              onClick={() => void save(question.answerId!)}>
              {savingAnswer === question.answerId ? 'Saving…' : 'Save score'}
            </button>}
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
        ? <button disabled={finalizing || Boolean(savingAnswer)} onClick={() => void finalize()}>
          {finalizing ? 'Finalizing…' : 'Finalize and release result'}
        </button>
        : <>
          <h2>Total score: {submission.totalScore} / {submission.maxScore} ({submission.percentage}%)</h2>
          <p><strong>Outcome:</strong> {submission.outcome === 'PASSED' ? 'Passed' : 'Not selected'}</p>
        </>}
      {finalizeNotice && <div
        className={`toast ${finalizeNotice.kind === 'error' ? 'toast-error' : 'toast-success'}`}
        role={finalizeNotice.kind === 'error' ? 'alert' : 'status'} aria-live="polite">
        <span>{finalizeNotice.text}</span>
        <button type="button" className="toast-close" aria-label="Dismiss"
          onClick={() => setFinalizeNotice(undefined)}>×</button>
      </div>}
      {!pending && <div className="compact-actions">
        <button className="secondary-button"
          disabled={aiBusy === 'email' || coaching?.status !== 'APPROVED'}
          onClick={() => void emailResult()}
          title="Email the candidate their outcome, score, and the approved coaching plan (needs coaching approved first).">
          {aiBusy === 'email' ? 'Sending…' : '📧 Email result to candidate'}</button>
      </div>}
      {!pending && <div className="coaching-panel">
        <h2>Candidate coaching feedback (AI)</h2>
        {!coaching && <button className="secondary-button" disabled={aiBusy === 'coach'} onClick={() => void genCoaching()}>
          {aiBusy === 'coach' ? 'Generating…' : '🤖 Generate candidate feedback'}</button>}
        {coaching && <>
          <p className={coaching.leakageSafe ? 'status-message' : 'error-message'}>
            Leakage guard: {coaching.leakageSafe ? 'clean' : `flagged — ${coaching.leakageFlags.join('; ')}`} · status {coaching.status}</p>
          <Markdown className="coaching-content" content={coaching.content} />
          {coaching.status !== 'APPROVED'
            ? <button disabled={aiBusy === 'approve'} onClick={() => void releaseCoaching()}>
              {aiBusy === 'approve' ? 'Approving…' : 'Approve & release to candidate'}</button>
            : <p className="status-message">Released to the candidate.</p>}
        </>}
      </div>}
    </section>
  </main>
}
