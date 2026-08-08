import { useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'

import { interviewApi, type AdaptiveView } from '../api/interviewApi'

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'Something went wrong'
}

/** Candidate view of an adaptive interview: one agent-chosen question at a time. Starting the
 *  route creates (or resumes) the session; each answer drives the agent to the next move. */
export function AdaptiveSessionPage() {
  const { assignmentId } = useParams<{ assignmentId: string }>()
  const navigate = useNavigate()
  const [view, setView] = useState<AdaptiveView | null>(null)
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

  if (!view) {
    return (
      <main className="dashboard">
        <h1>Adaptive interview</h1>
        {error ? <p role="alert" className="empty-state">{error}</p>
          : <p className="empty-state">Starting your adaptive interview…</p>}
      </main>
    )
  }

  const finished = view.done || view.currentQuestion === null

  return (
    <main className="dashboard">
      <h1>Adaptive interview</h1>
      <p className="assignment-window-note">
        Question {view.turnsUsed} of up to {view.maxTurns}</p>
      {error && <p role="alert" className="empty-state">{error}</p>}

      {finished ? (
        <section className="workspace-content">
          <h2>Interview complete</h2>
          <p>Thanks — your responses are recorded. Your interviewer will review them and
            share the result.</p>
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
