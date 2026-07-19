import { useEffect, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  interviewApi, type AdminQuestion, type Interview, type Profile, type QuestionType,
} from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

const initialForm = {
  title: '', description: '', skills: '', difficulty: 'MEDIUM',
  questionMode: 'MANUAL', durationMinutes: 60, questionCount: 5, passingPercentage: 70,
}

type QuestionDraft = {
  id?: string
  order: number
  prompt: string
  maxScore: number
  type: QuestionType
  optionsText: string
  correctText: string
}

const emptyQuestion = (order = 1): QuestionDraft => ({
  order, prompt: '', maxScore: 10, type: 'LONG_TEXT', optionsText: '', correctText: '',
})

function messageOf(error: unknown) {
  return error instanceof Error ? error.message : 'Unexpected request failure'
}

function InterviewCard({ interview, candidates, notify, reload }: {
  interview: Interview
  candidates: Profile[]
  notify: (message: string, error?: boolean) => void
  reload: () => Promise<void>
}) {
  const [questions, setQuestions] = useState<AdminQuestion[]>([])
  const [draft, setDraft] = useState<QuestionDraft>(emptyQuestion())
  const [candidateId, setCandidateId] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const isMcq = draft.type === 'MCQ_SINGLE' || draft.type === 'MCQ_MULTIPLE'

  async function loadQuestions() {
    try {
      const loaded = await interviewApi.listQuestions(interview.id)
      setQuestions(loaded)
      if (!draft.id) setDraft(emptyQuestion(loaded.length + 1))
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  useEffect(() => { void loadQuestions() }, [interview.id])

  async function saveQuestion(event: FormEvent) {
    event.preventDefault()
    const body = {
      order: draft.order,
      prompt: draft.prompt,
      maxScore: draft.maxScore,
      type: draft.type,
      options: isMcq
        ? draft.optionsText.split('\n').map((value) => value.trim()).filter(Boolean)
        : [],
      correctAnswers: isMcq
        ? draft.correctText.split(',').map((value) => value.trim()).filter(Boolean)
        : [],
    }
    try {
      if (draft.id) await interviewApi.updateQuestion(interview.id, draft.id, body)
      else await interviewApi.addQuestion(interview.id, body)
      notify(draft.id ? 'Question updated.' : 'Question added.')
      setDraft(emptyQuestion(questions.length + (draft.id ? 0 : 2)))
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  function edit(question: AdminQuestion) {
    setDraft({
      id: question.id,
      order: question.order,
      prompt: question.prompt,
      maxScore: question.maxScore,
      type: question.type,
      optionsText: question.options.join('\n'),
      correctText: question.correctAnswers.join(', '),
    })
  }

  async function remove(questionId: string) {
    if (!window.confirm('Delete this draft question?')) return
    try {
      await interviewApi.deleteQuestion(interview.id, questionId)
      notify('Question deleted.')
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function generate() {
    notify('Generating questions through the AI Gateway…')
    try {
      await interviewApi.generateQuestions(interview.id)
      notify('AI questions generated and saved in PostgreSQL.')
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function publish() {
    try {
      await interviewApi.publish(interview.id)
      notify('Interview published.')
      await reload()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  async function assign(event: FormEvent) {
    event.preventDefault()
    try {
      await interviewApi.assign(interview.id, {
        candidateId,
        startsAt: new Date(startsAt).toISOString(),
        endsAt: new Date(endsAt).toISOString(),
        maxAttempts: 1,
      })
      notify('Candidate assignment scheduled.')
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  return (
    <article className="card interview-card">
      <div className="card-heading">
        <div><h2>{interview.title}</h2><p>{interview.skills.join(', ')} · {interview.difficulty} · {interview.durationMinutes} min</p></div>
        <span className="badge">{interview.status}</span>
      </div>
      <p><strong>Questions:</strong> {questions.length} / {interview.questionCount}</p>
      <p><strong>Passing score:</strong> {interview.passingPercentage}%</p>

      {questions.length > 0 && <div className="question-list">
        {questions.map((question) => (
          <div className="question-preview" key={question.id}>
            <div><strong>{question.order}. {question.type.replaceAll('_', ' ')}</strong><p>{question.prompt}</p></div>
            {question.options.length > 0 && <ol type="A">{question.options.map((option) => <li key={option}>{option}</li>)}</ol>}
            {interview.status === 'DRAFT' && <div className="compact-actions">
              <button type="button" className="secondary-button" onClick={() => edit(question)}>Edit</button>
              <button type="button" className="danger-button" onClick={() => void remove(question.id)}>Delete</button>
            </div>}
          </div>
        ))}
      </div>}

      {interview.status === 'DRAFT' && <>
        {interview.questionMode === 'DIRECT_LLM' &&
          <button type="button" onClick={() => void generate()}>Generate AI questions</button>}
        {interview.questionMode === 'MANUAL' && <form className="question-builder" onSubmit={(event) => void saveQuestion(event)}>
          <h3>{draft.id ? 'Edit question' : 'Add question'}</h3>
          <div className="inline-fields">
            <label>Order<input type="number" min="1" max="100" value={draft.order} onChange={(e) => setDraft({...draft, order: Number(e.target.value)})} /></label>
            <label>Type<select value={draft.type} onChange={(e) => setDraft({...draft, type: e.target.value as QuestionType})}>
              <option value="LONG_TEXT">Long text</option>
              <option value="SHORT_TEXT">Short text</option>
              <option value="MCQ_SINGLE">MCQ — single answer</option>
              <option value="MCQ_MULTIPLE">MCQ — multiple answers</option>
            </select></label>
            <label>Points<input type="number" min="1" max="100" value={draft.maxScore} onChange={(e) => setDraft({...draft, maxScore: Number(e.target.value)})} /></label>
          </div>
          <label>Prompt<textarea required value={draft.prompt} onChange={(e) => setDraft({...draft, prompt: e.target.value})} /></label>
          {isMcq && <>
            <label>Options (one per line)<textarea required value={draft.optionsText} onChange={(e) => setDraft({...draft, optionsText: e.target.value})} /></label>
            <label>Correct answer{draft.type === 'MCQ_MULTIPLE' ? 's (comma separated)' : ''}
              <input required value={draft.correctText} onChange={(e) => setDraft({...draft, correctText: e.target.value})} />
            </label>
          </>}
          <div className="compact-actions">
            <button type="submit">{draft.id ? 'Save changes' : 'Add question'}</button>
            {draft.id && <button type="button" className="secondary-button" onClick={() => setDraft(emptyQuestion(questions.length + 1))}>Cancel</button>}
          </div>
        </form>}
        <button type="button" disabled={questions.length !== interview.questionCount} onClick={() => void publish()}>
          Publish {questions.length !== interview.questionCount && `(${interview.questionCount - questions.length} remaining)`}
        </button>
      </>}

      {interview.status === 'PUBLISHED' && <form className="assignment-form" onSubmit={(event) => void assign(event)}>
        <label>Candidate<select required value={candidateId} onChange={(e) => setCandidateId(e.target.value)}>
          <option value="">Select a candidate</option>
          {candidates.map((candidate) => <option key={candidate.id} value={candidate.id}>{candidate.displayName} — {candidate.email}</option>)}
        </select></label>
        <label>Starts<input required type="datetime-local" value={startsAt} onChange={(e) => setStartsAt(e.target.value)} /></label>
        <label>Ends<input required type="datetime-local" value={endsAt} onChange={(e) => setEndsAt(e.target.value)} /></label>
        <button type="submit">Assign candidate</button>
      </form>}
    </article>
  )
}

export function InterviewerDashboard() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [interviews, setInterviews] = useState<Interview[]>([])
  const [candidates, setCandidates] = useState<Profile[]>([])
  const [form, setForm] = useState(initialForm)
  const [message, setMessage] = useState('')
  const [hasError, setHasError] = useState(false)

  const notify = (text: string, error = false) => { setMessage(text); setHasError(error) }
  const load = async () => {
    const [owned, availableCandidates] = await Promise.all([
      interviewApi.listOwned(), interviewApi.candidates(),
    ])
    setInterviews(owned)
    setCandidates(availableCandidates)
  }

  useEffect(() => { void load().catch((error) => notify(messageOf(error), true)) }, [])

  async function create(event: FormEvent) {
    event.preventDefault()
    try {
      await interviewApi.create({
        ...form,
        skills: form.skills.split(',').map((skill) => skill.trim()).filter(Boolean),
      })
      setForm(initialForm)
      notify('Draft interview created.')
      await load()
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Interviewer workspace</p><h1>Interview management</h1></div>
        <div className="compact-actions"><button onClick={() => navigate('/interviewer/submissions')}>Review submissions</button><button className="secondary-button" onClick={auth.logout}>Sign out</button></div>
      </div>
      {message && <p className={hasError ? 'error-message' : 'status-message'}>{message}</p>}
      <form className="form-grid" onSubmit={(event) => void create(event)}>
        <h2>Create interview draft</h2>
        <label>Title<input required value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} /></label>
        <label>Description<textarea required value={form.description} onChange={(e) => setForm({...form, description: e.target.value})} /></label>
        <label>Skills (comma separated)<input required value={form.skills} onChange={(e) => setForm({...form, skills: e.target.value})} /></label>
        <label>Difficulty<select value={form.difficulty} onChange={(e) => setForm({...form, difficulty: e.target.value})}><option>EASY</option><option>MEDIUM</option><option>HARD</option><option>MIXED</option></select></label>
        <label>Question mode<select value={form.questionMode} onChange={(e) => setForm({...form, questionMode: e.target.value})}><option>MANUAL</option><option>DIRECT_LLM</option></select></label>
        <label>Duration (minutes)<input type="number" min="5" max="480" value={form.durationMinutes} onChange={(e) => setForm({...form, durationMinutes: Number(e.target.value)})} /></label>
        <label>Question count<input type="number" min="1" max="100" value={form.questionCount} onChange={(e) => setForm({...form, questionCount: Number(e.target.value)})} /></label>
        <label>Passing percentage<input type="number" min="1" max="100" value={form.passingPercentage} onChange={(e) => setForm({...form, passingPercentage: Number(e.target.value)})} /></label>
        <button type="submit">Create draft</button>
      </form>
      <div className="card-grid">
        {interviews.map((interview) => <InterviewCard key={interview.id} interview={interview}
          candidates={candidates} notify={notify} reload={load} />)}
      </div>
    </main>
  )
}
