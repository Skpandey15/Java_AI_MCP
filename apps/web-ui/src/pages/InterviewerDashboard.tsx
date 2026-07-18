import { useEffect, useState, type FormEvent } from 'react'
import { interviewApi, type Interview } from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

const initialForm = {
  title: '', description: '', skills: '', difficulty: 'MEDIUM',
  questionMode: 'MANUAL', durationMinutes: 60, questionCount: 5,
}

export function InterviewerDashboard() {
  const auth = useAuth()
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [form, setForm] = useState(initialForm)
  const [candidateId, setCandidateId] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [message, setMessage] = useState('')

  const load = () => interviewApi.listOwned().then(setInterviews)
    .catch(() => setMessage('Unable to load interviews.'))

  useEffect(() => { void load() }, [])

  async function create(event: FormEvent) {
    event.preventDefault()
    await interviewApi.create({
      ...form,
      skills: form.skills.split(',').map((skill) => skill.trim()).filter(Boolean),
    })
    setForm(initialForm)
    setMessage('Draft interview created.')
    await load()
  }

  async function publish(id: string) {
    await interviewApi.publish(id)
    setMessage('Interview published.')
    await load()
  }

  async function assign(event: FormEvent, id: string) {
    event.preventDefault()
    await interviewApi.assign(id, {
      candidateId,
      startsAt: new Date(startsAt).toISOString(),
      endsAt: new Date(endsAt).toISOString(),
      maxAttempts: 1,
    })
    setMessage('Candidate assignment scheduled.')
  }

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Interviewer workspace</p><h1>Interview management</h1></div>
        <button className="secondary-button" onClick={auth.logout}>Sign out</button>
      </div>
      {message && <p className="status-message">{message}</p>}
      <form className="form-grid" onSubmit={(event) => void create(event)}>
        <h2>Create interview draft</h2>
        <label>Title<input required value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} /></label>
        <label>Description<textarea required value={form.description} onChange={(e) => setForm({...form, description: e.target.value})} /></label>
        <label>Skills (comma separated)<input required value={form.skills} onChange={(e) => setForm({...form, skills: e.target.value})} /></label>
        <label>Difficulty<select value={form.difficulty} onChange={(e) => setForm({...form, difficulty: e.target.value})}><option>EASY</option><option>MEDIUM</option><option>HARD</option><option>MIXED</option></select></label>
        <label>Question mode<select value={form.questionMode} onChange={(e) => setForm({...form, questionMode: e.target.value})}><option>MANUAL</option><option>DIRECT_LLM</option><option>RAG</option></select></label>
        <label>Duration (minutes)<input type="number" min="5" max="480" value={form.durationMinutes} onChange={(e) => setForm({...form, durationMinutes: Number(e.target.value)})} /></label>
        <label>Question count<input type="number" min="1" max="100" value={form.questionCount} onChange={(e) => setForm({...form, questionCount: Number(e.target.value)})} /></label>
        <button type="submit">Create draft</button>
      </form>
      <div className="card-grid">
        {interviews.map((interview) => (
          <article className="card" key={interview.id}>
            <h2>{interview.title}</h2>
            <p>{interview.skills.join(', ')} · {interview.difficulty} · {interview.durationMinutes} min</p>
            <p><strong>Status:</strong> {interview.status}</p>
            {interview.status === 'DRAFT' && <button onClick={() => void publish(interview.id)}>Publish</button>}
            {interview.status === 'PUBLISHED' && (
              <form className="assignment-form" onSubmit={(event) => void assign(event, interview.id)}>
                <label>Candidate profile ID<input required value={candidateId} onChange={(e) => setCandidateId(e.target.value)} /></label>
                <label>Starts<input required type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
                <label>Ends<input required type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} /></label>
                <button type="submit">Assign candidate</button>
              </form>
            )}
          </article>
        ))}
      </div>
    </main>
  )
}
