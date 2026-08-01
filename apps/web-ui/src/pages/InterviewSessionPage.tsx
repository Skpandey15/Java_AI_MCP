import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { interviewApi, type InterviewSession, type Question } from '../api/interviewApi'

function selectedValues(content: string | undefined): string[] {
  if (!content) return []
  return content.split('\n').map((value) => value.trim()).filter(Boolean)
}

export function InterviewSessionPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()
  const [session, setSession] = useState<InterviewSession>()
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [versions, setVersions] = useState<Record<string, number>>({})
  const [now, setNow] = useState(Date.now())
  const [status, setStatus] = useState('')
  const [statusError, setStatusError] = useState(false)
  const [statusNonce, setStatusNonce] = useState(0)

  const note = (text: string, error = false) => {
    setStatus(text); setStatusError(error); setStatusNonce((n) => n + 1)
  }
  useEffect(() => {
    if (!status || !session) return
    const timer = window.setTimeout(() => setStatus(''), 6000)
    return () => window.clearTimeout(timer)
  }, [status, statusNonce, session])

  useEffect(() => {
    interviewApi.loadSession(sessionId).then((loaded) => {
      setSession(loaded)
      setDrafts(Object.fromEntries(loaded.answers.map((answer) => [answer.questionId, answer.content])))
      setVersions(Object.fromEntries(loaded.answers.map((answer) => [answer.questionId, answer.version])))
    }).catch((error: unknown) => setStatus(error instanceof Error ? error.message : 'Unable to load this interview session.'))
  }, [sessionId])

  useEffect(() => {
    if (session?.state !== 'IN_PROGRESS') return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [session?.state])

  const secondsLeft = useMemo(() => session
    ? Math.max(0, Math.floor((new Date(session.expiresAt).getTime() - now) / 1000))
    : 0, [now, session])

  async function save(questionId: string, content = drafts[questionId] ?? '') {
    if (!session || session.state !== 'IN_PROGRESS') return
    note('Saving…')
    try {
      const answer = await interviewApi.saveAnswer(
        session.id, questionId, content, versions[questionId] ?? 0)
      setVersions((current) => ({ ...current, [questionId]: answer.version }))
      note('All answers saved.')
    } catch (error) {
      note(error instanceof Error ? error.message : 'Save failed. Reload before continuing.', true)
    }
  }

  function chooseSingle(questionId: string, value: string) {
    setDrafts((current) => ({ ...current, [questionId]: value }))
    void save(questionId, value)
  }

  function toggleMultiple(questionId: string, option: string, checked: boolean) {
    const selected = new Set(selectedValues(drafts[questionId]))
    if (checked) selected.add(option)
    else selected.delete(option)
    const content = [...selected].join('\n')
    setDrafts((current) => ({ ...current, [questionId]: content }))
    void save(questionId, content)
  }

  async function submit() {
    if (!session || !window.confirm('Submit this interview? Answers cannot be changed afterward.')) return
    try {
      const submitted = await interviewApi.submitSession(session.id)
      setSession(submitted)
      note('Interview submitted successfully.')
    } catch (error) {
      note(error instanceof Error ? error.message : 'Unable to submit interview.', true)
    }
  }

  function answerControl(question: Question) {
    const disabled = session?.state !== 'IN_PROGRESS' || secondsLeft === 0
    if (question.type === 'MCQ_SINGLE') {
      return <fieldset disabled={disabled} className="choice-list">
        <legend>Select one answer</legend>
        {question.options.map((option) => <label key={option}>
          <input type="radio" name={question.id} checked={drafts[question.id] === option}
            onChange={() => chooseSingle(question.id, option)} /> {option}
        </label>)}
      </fieldset>
    }
    if (question.type === 'MCQ_MULTIPLE') {
      const selected = selectedValues(drafts[question.id])
      return <fieldset disabled={disabled} className="choice-list">
        <legend>Select all that apply</legend>
        {question.options.map((option) => <label key={option}>
          <input type="checkbox" checked={selected.includes(option)}
            onChange={(event) => toggleMultiple(question.id, option, event.target.checked)} /> {option}
        </label>)}
      </fieldset>
    }
    return <textarea
      className={question.type === 'SHORT_TEXT' ? 'short-answer' : ''}
      disabled={disabled}
      value={drafts[question.id] ?? ''}
      onChange={(event) => setDrafts((current) => ({ ...current, [question.id]: event.target.value }))}
      onBlur={() => void save(question.id)}
      maxLength={question.type === 'SHORT_TEXT' ? 1000 : 12000}
      aria-label={`Answer for question ${question.order}`}
    />
  }

  if (!session) return <main className="dashboard"><p>{status || 'Loading interview…'}</p></main>

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">{session.state === 'IN_PROGRESS' ? 'Active interview' : 'Interview complete'}</p><h1>Interview session</h1></div>
        <div className={session.state === 'IN_PROGRESS' ? 'timer timer-floating' : 'timer'} aria-live="polite">
          {session.state === 'IN_PROGRESS'
            ? `⏱ ${Math.floor(secondsLeft / 60)}:${String(secondsLeft % 60).padStart(2, '0')}`
            : 'Submitted'}
        </div>
      </div>
      <p><strong>State:</strong> {session.state}</p>
      <p className="status-message">{session.state === 'IN_PROGRESS'
        ? 'Answers save automatically.' : 'Your responses are locked.'}</p>
      {status && <div className={`toast ${statusError ? 'toast-error' : 'toast-success'}`}
        role={statusError ? 'alert' : 'status'} aria-live="polite">
        <span>{status}</span>
        <button type="button" className="toast-close" aria-label="Dismiss" onClick={() => setStatus('')}>×</button>
      </div>}
      {session.questions.map((question) => (
        <section className="question-card" key={question.id}>
          <div className="question-meta"><span>{question.type.replaceAll('_', ' ')}</span><span>{question.maxScore} points</span></div>
          <h2>{question.order}. {question.prompt}</h2>
          {answerControl(question)}
        </section>
      ))}
      <div className="actions">
        <button className="secondary-button" onClick={() => navigate('/candidate')}>Dashboard</button>
        {session.state === 'SUBMITTED'
          ? <button onClick={() => navigate(`/candidate/sessions/${session.id}/result`)}>View result</button>
          : <button disabled={secondsLeft === 0} onClick={() => void submit()}>Submit interview</button>}
      </div>
    </main>
  )
}
