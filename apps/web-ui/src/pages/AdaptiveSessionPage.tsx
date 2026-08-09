import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { interviewApi, type AdaptiveResult, type AdaptiveView } from '../api/interviewApi'

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong'
}

/** Candidate view of an adaptive interview: one agent-chosen question at a time. Starting the
 *  route creates (or resumes) the session; each answer drives the agent to the next move. */
export function AdaptiveSessionPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>()
  const navigate = useNavigate()
  const [view, setView] = useState<AdaptiveView | null>(null)
  const [result, setResult] = useState<AdaptiveResult | null>(null)
  const [resultError, setResultError] = useState('')
  const [answer, setAnswer] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const started = useRef(false)

  useEffect(() => {
    if (started.current || !assignmentId) return
    started.current = true
    interviewApi.startAdaptiveSession(assignmentId)
      .then(setView)
      .catch((reason: unknown) => setError(messageOf(reason)))
  }, [assignmentId])

  const submit = useCallback(async () => {
    if (!view) return
    setBusy(true)
    setError('')
    try {
      const next = await interviewApi.answerAdaptive(view.sessionId, answer)
      setView(next)
      setAnswer('')
    } catch (reason) {
      setError(messageOf(reason))
    } finally {
      setBusy(false)
    }
  }, [view, answer])

  // Once the interview concludes, load the candidate's scored transcript for immediate feedback.
  const finished = view != null && (view.done || view.currentQuestion === null)
  useEffect(() => {
    if (!finished || !view || result || resultError) return
    interviewApi.adaptiveResult(view.sessionId)
      .then(setResult)
      .catch((reason: unknown) => { setResultError(messageOf(reason)) })
  }, [finished, view, result, resultError])

  if (!view) {
    return (
      <main className="dashboard">
        <h1>Adaptive interview</h1>
        {error ? <p role="alert" className="empty-state">{error}</p>
          : <p className="empty-state">Starting your adaptive interview…</p>}
      </main>
    )
  }

  return (
    <main className="dashboard">
      <h1>Adaptive interview</h1>
      <p className="assignment-window-note">
        Question {view.turnsUsed} of up to {view.maxTurns}</p>
      {error && <p role="alert" className="empty-state">{error}</p>}

      {finished ? (
        <section className="workspace-content">
          <h2>Interview complete</h2>
          {result ? (
            <>
              <div className="adaptive-result-summary">
                <span className={`result-badge ${
                  result.overallScore == null ? 'neutral' : result.passed ? 'pass' : 'fail'}`}>
                  {result.overallScore == null ? 'No score'
                    : result.passed ? 'Passed' : 'Below passing'}</span>
                <p><strong>Overall AI score:</strong> {result.overallScore ?? '—'} / 100
                  {'  ·  '}<strong>Passing:</strong> {result.passingPercentage}%</p>
              </div>
              <p className="assignment-window-note">Review each question, your answer, and the
                AI's feedback below to see where to improve.</p>
              {result.turns.map((turn) => (
                <div className="adaptive-turn" key={turn.ordinal}>
                  <div className="question-meta">
                    <span>Q{turn.ordinal} · {turn.skill} · {turn.difficulty}</span>
                    <span>{turn.score != null ? `AI score ${turn.score}/100` : 'unscored'}</span>
                  </div>
                  <h3>{turn.question}</h3>
                  <div className="answer-panel"><strong>Your answer</strong>
                    <pre>{turn.answer || 'No answer submitted'}</pre></div>
                  {turn.rationale && <p className="ai-suggestion">🤖 AI feedback: {turn.rationale}</p>}
                </div>
              ))}
            </>
          ) : resultError ? (
            <p role="alert">Your responses are recorded, but we couldn't load your detailed
              feedback ({resultError}). You can view it later from your dashboard.</p>
          ) : (
            <p>Thanks — your responses are recorded. Preparing your feedback…</p>
          )}
          <button type="button" onClick={() => navigate('/candidate')}>Back to dashboard</button>
        </section>
      ) : (
        <section className="workspace-content">
          <p className="assignment-window-note">
            {view.currentQuestion!.skill} · {view.currentQuestion!.difficulty}</p>
          <h2>{view.currentQuestion!.prompt}</h2>
          <label>Your answer<textarea rows={10} value={answer}
            onChange={(event) => setAnswer(event.target.value)}
            placeholder="Type your answer…" /></label>
          <button type="button" disabled={busy || !answer.trim()} onClick={() => void submit()}>
            {busy ? 'Submitting…' : 'Submit answer'}</button>
        </section>
      )}
    </main>
  )
}
