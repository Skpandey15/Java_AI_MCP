import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { interviewApi, type InterviewSession } from '../api/interviewApi'

export function InterviewSessionPage() {
  const { sessionId = '' } = useParams()
  const navigate = useNavigate()
  const [session, setSession] = useState<InterviewSession>()
  const [drafts, setDrafts] = useState<Record<string, string>>({})
  const [versions, setVersions] = useState<Record<string, number>>({})
  const [now, setNow] = useState(Date.now())
  const [status, setStatus] = useState('')

  useEffect(() => {
    interviewApi.loadSession(sessionId).then((loaded) => {
      setSession(loaded)
      setDrafts(Object.fromEntries(loaded.answers.map((answer) => [answer.questionId, answer.content])))
      setVersions(Object.fromEntries(loaded.answers.map((answer) => [answer.questionId, answer.version])))
    }).catch(() => setStatus('Unable to load this interview session.'))
  }, [sessionId])

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [])

  const secondsLeft = useMemo(() => session
    ? Math.max(0, Math.floor((new Date(session.expiresAt).getTime() - now) / 1000))
    : 0, [now, session])

  async function save(questionId: string) {
    if (!session || session.state !== 'IN_PROGRESS') return
    setStatus('Saving…')
    try {
      const answer = await interviewApi.saveAnswer(
        session.id, questionId, drafts[questionId] ?? '', versions[questionId] ?? 0)
      setVersions((current) => ({ ...current, [questionId]: answer.version }))
      setStatus('All answers saved.')
    } catch {
      setStatus('Save conflict detected. Reload before continuing.')
    }
  }

  async function submit() {
    if (!session || !window.confirm('Submit this interview? Answers cannot be changed afterward.')) return
    const submitted = await interviewApi.submitSession(session.id)
    setSession(submitted)
    setStatus('Interview submitted successfully.')
  }

  if (!session) return <main className="dashboard"><p>{status || 'Loading interview…'}</p></main>

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Active interview</p><h1>Interview session</h1></div>
        <div className="timer" aria-live="polite">{Math.floor(secondsLeft / 60)}:{String(secondsLeft % 60).padStart(2, '0')}</div>
      </div>
      <p><strong>State:</strong> {session.state}</p>
      <p className="status-message">{status || 'Answers save when you leave each answer field.'}</p>
      {session.questions.map((question) => (
        <section className="question-card" key={question.id}>
          <h2>{question.order}. {question.prompt}</h2>
          <p>{question.maxScore} points</p>
          <textarea
            disabled={session.state !== 'IN_PROGRESS' || secondsLeft === 0}
            value={drafts[question.id] ?? ''}
            onChange={(event) => setDrafts((current) => ({ ...current, [question.id]: event.target.value }))}
            onBlur={() => void save(question.id)}
            aria-label={`Answer for question ${question.order}`}
          />
        </section>
      ))}
      <div className="actions">
        <button className="secondary" onClick={() => navigate('/candidate')}>Dashboard</button>
        <button disabled={session.state !== 'IN_PROGRESS' || secondsLeft === 0} onClick={() => void submit()}>Submit interview</button>
      </div>
    </main>
  )
}
