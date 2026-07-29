import { useEffect, useRef, useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  interviewApi, type AdminQuestion, type Interview, type Profile, type QuestionType,
} from '../api/interviewApi'
import { useAuth } from '../auth/AuthProvider'

const ecosystemTechnologies = {
  JAVA: [
    'Java', 'Spring Boot', 'Spring Framework', 'Spring MVC', 'Spring Security',
    'Spring Data JPA', 'Hibernate', 'Jakarta EE', 'Quarkus', 'Micronaut',
    'Maven', 'Gradle', 'JUnit', 'Mockito', 'Apache Kafka',
  ],
  PYTHON: [
    'Python', 'Django', 'Flask', 'FastAPI', 'Pydantic', 'SQLAlchemy',
    'Celery', 'pytest', 'NumPy', 'pandas', 'scikit-learn', 'PyTorch',
    'TensorFlow', 'LangChain', 'Jupyter',
  ],
  UI: [
    'HTML5', 'CSS3', 'JavaScript', 'TypeScript', 'React', 'Angular', 'Vue.js',
    'Svelte', 'Next.js', 'Nuxt', 'Vite', 'Tailwind CSS', 'Bootstrap',
    'Material UI', 'Redux', 'Jest', 'Vitest', 'Cypress', 'Playwright',
  ],
  DATABASE: [
    'PostgreSQL', 'MySQL', 'MariaDB', 'Oracle Database', 'Microsoft SQL Server',
    'SQLite', 'MongoDB', 'Redis', 'Apache Cassandra', 'Amazon DynamoDB',
    'Elasticsearch', 'Neo4j', 'CockroachDB', 'Snowflake', 'Google BigQuery',
  ],
  AI: [
    'Generative AI', 'Large Language Models', 'Prompt Engineering',
    'Retrieval-Augmented Generation (RAG)', 'AI Agents', 'Model Context Protocol (MCP)',
    'OpenAI API', 'Anthropic API', 'LangChain', 'LangGraph', 'LlamaIndex',
    'Hugging Face Transformers', 'PyTorch', 'TensorFlow', 'Vector Databases',
    'Embeddings', 'Model Evaluation', 'Fine-tuning', 'LiteLLM', 'Ollama', 'MLflow',
  ],
} as const

type Ecosystem = keyof typeof ecosystemTechnologies

const technologyDescriptions: Record<string, string> = {
  Java: 'A general-purpose JVM language widely used for enterprise and backend systems.',
  'Spring Boot': 'A Spring framework for building production-ready Java services with convention-based configuration.',
  'Spring Framework': 'A modular Java application framework providing dependency injection and infrastructure support.',
  'Spring MVC': 'Spring’s web framework for HTTP controllers, request handling, and server-rendered applications.',
  'Spring Security': 'A framework for authentication, authorization, and protection against common application attacks.',
  'Spring Data JPA': 'A repository abstraction that simplifies relational data access in Spring applications.',
  Hibernate: 'An object-relational mapping framework that maps Java entities to relational database tables.',
  'Jakarta EE': 'A collection of enterprise Java specifications for web, persistence, messaging, and distributed systems.',
  Quarkus: 'A Kubernetes-focused Java framework optimized for fast startup and low memory usage.',
  Micronaut: 'A JVM framework using compile-time dependency injection for lightweight services and serverless applications.',
  Maven: 'A Java build and dependency-management tool based on declarative project configuration.',
  Gradle: 'A flexible build automation tool using incremental execution and programmable build scripts.',
  JUnit: 'The standard Java unit-testing framework for defining and running automated tests.',
  Mockito: 'A Java mocking framework used to isolate dependencies in unit tests.',
  'Apache Kafka': 'A distributed event-streaming platform for high-throughput messaging and data pipelines.',
  Python: 'A general-purpose language popular for web development, automation, data science, and AI.',
  Django: 'A batteries-included Python web framework with ORM, routing, forms, and administration features.',
  Flask: 'A lightweight Python web framework suited to small services and flexible application architectures.',
  FastAPI: 'A typed Python API framework with automatic validation and OpenAPI documentation.',
  Pydantic: 'A Python library for typed data validation, parsing, and application settings.',
  SQLAlchemy: 'A Python SQL toolkit and ORM supporting expressive relational database access.',
  Celery: 'A distributed Python task queue for background jobs and scheduled processing.',
  pytest: 'A Python testing framework offering concise tests, fixtures, and a rich plugin ecosystem.',
  NumPy: 'A numerical-computing library providing fast multidimensional arrays and mathematical operations.',
  pandas: 'A data-analysis library built around tabular DataFrames and data transformation tools.',
  'scikit-learn': 'A machine-learning library for preprocessing, classical models, evaluation, and pipelines.',
  PyTorch: 'A tensor and deep-learning framework widely used for research and production AI workloads.',
  TensorFlow: 'An end-to-end machine-learning platform for training and serving neural-network models.',
  LangChain: 'A framework for composing LLM applications from models, tools, retrieval, and memory.',
  Jupyter: 'An interactive notebook environment for executable code, analysis, and documentation.',
  HTML5: 'The semantic markup standard used to structure modern web pages and applications.',
  CSS3: 'The styling language used for responsive layout, presentation, and animation on the web.',
  JavaScript: 'The primary programming language for interactive browser and full-stack web applications.',
  TypeScript: 'A typed superset of JavaScript that improves tooling and large-codebase maintainability.',
  React: 'A component-based JavaScript library for building interactive user interfaces.',
  Angular: 'A full-featured TypeScript framework for structured, large-scale web applications.',
  'Vue.js': 'A progressive JavaScript framework for reactive, component-based user interfaces.',
  Svelte: 'A compiler-based UI framework that produces small, efficient browser code.',
  'Next.js': 'A React framework supporting routing, server rendering, data fetching, and full-stack applications.',
  Nuxt: 'A Vue framework for server rendering, routing, and full-stack web development.',
  Vite: 'A fast frontend development server and production build tool.',
  'Tailwind CSS': 'A utility-first CSS framework for composing designs directly in markup.',
  Bootstrap: 'A responsive UI toolkit providing layout utilities and reusable components.',
  'Material UI': 'A React component library implementing Google’s Material Design system.',
  Redux: 'A predictable state container commonly used for complex JavaScript application state.',
  Jest: 'A JavaScript testing framework with assertions, mocking, and snapshot testing.',
  Vitest: 'A Vite-native unit-testing framework with fast execution and Jest-compatible APIs.',
  Cypress: 'A browser-based end-to-end testing framework with interactive debugging.',
  Playwright: 'A cross-browser automation framework for reliable end-to-end testing.',
  PostgreSQL: 'An open-source relational database known for correctness, extensibility, and advanced SQL.',
  MySQL: 'A widely deployed relational database used by web and transactional applications.',
  MariaDB: 'A community-developed MySQL-compatible relational database.',
  'Oracle Database': 'An enterprise relational database with extensive security, availability, and analytics features.',
  'Microsoft SQL Server': 'Microsoft’s relational database platform with transactional and analytics tooling.',
  SQLite: 'An embedded, serverless relational database stored in a single local file.',
  MongoDB: 'A document database that stores flexible JSON-like records.',
  Redis: 'An in-memory data store used for caching, messaging, sessions, and fast data structures.',
  'Apache Cassandra': 'A distributed wide-column database designed for availability and large write workloads.',
  'Amazon DynamoDB': 'A managed key-value and document database with automatic scaling on AWS.',
  Elasticsearch: 'A distributed search and analytics engine built around indexed documents.',
  Neo4j: 'A graph database designed for highly connected data and relationship queries.',
  CockroachDB: 'A distributed SQL database designed for horizontal scale and resilience.',
  Snowflake: 'A managed cloud data platform for warehousing, analytics, and data sharing.',
  'Google BigQuery': 'A serverless cloud data warehouse for large-scale SQL analytics.',
  'Generative AI': 'AI systems that create new text, images, code, or other content from learned patterns.',
  'Large Language Models': 'Neural language models trained at scale to understand and generate natural language.',
  'Prompt Engineering': 'The practice of designing instructions and context that guide model behavior.',
  'Retrieval-Augmented Generation (RAG)': 'An architecture that grounds model responses in retrieved external knowledge.',
  'AI Agents': 'Model-driven systems that reason, use tools, and take actions toward a goal.',
  'Model Context Protocol (MCP)': 'An open protocol for connecting AI applications to tools and contextual data.',
  'OpenAI API': 'An API platform for integrating OpenAI language, reasoning, audio, and multimodal models.',
  'Anthropic API': 'An API platform for building applications with Anthropic Claude models.',
  LangGraph: 'A graph-based orchestration framework for stateful and multi-step agent workflows.',
  LlamaIndex: 'A framework for connecting LLM applications with private and external data sources.',
  'Hugging Face Transformers': 'A library providing pretrained transformer models and training utilities.',
  'Vector Databases': 'Databases optimized for storing embeddings and performing similarity search.',
  Embeddings: 'Dense numerical representations that capture semantic similarity between data items.',
  'Model Evaluation': 'The systematic measurement of model quality, safety, reliability, and task performance.',
  'Fine-tuning': 'Additional model training on specialized examples to adapt behavior or domain knowledge.',
  LiteLLM: 'A model gateway offering a consistent API across multiple LLM providers.',
  Ollama: 'A local runtime for downloading and serving open-weight language models.',
  MLflow: 'A platform for tracking experiments, packaging models, and managing ML lifecycles.',
}

const initialForm = {
  title: '', description: '', ecosystem: 'JAVA' as Ecosystem, technologies: ['Java'] as string[],
  difficulty: 'MEDIUM',
  questionMode: 'MANUAL', durationMinutes: 60, questionCount: 0, passingPercentage: 70,
  mcqSingle: 0, mcqMultiple: 0, shortText: 0, longText: 0,
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

export function InterviewCard({ interview, candidates, notify, reload, showQuestions = true, showAssignment = true }: {
  interview: Interview
  candidates: Profile[]
  notify: (message: string, error?: boolean) => void
  reload: () => Promise<void>
  showQuestions?: boolean
  showAssignment?: boolean
}) {
  const [questions, setQuestions] = useState<AdminQuestion[]>([])
  const [draft, setDraft] = useState<QuestionDraft>(emptyQuestion())
  const [candidateId, setCandidateId] = useState('')
  const [startsAt, setStartsAt] = useState('')
  const [endsAt, setEndsAt] = useState('')
  const [generating, setGenerating] = useState(false)
  const questionFormRef = useRef<HTMLFormElement>(null)
  const isMcq = draft.type === 'MCQ_SINGLE' || draft.type === 'MCQ_MULTIPLE'
  const actualComposition = questions.reduce((counts, question) => ({
    ...counts,
    [question.type]: counts[question.type] + 1,
  }), {MCQ_SINGLE: 0, MCQ_MULTIPLE: 0, SHORT_TEXT: 0, LONG_TEXT: 0})
  const expectedComposition = interview.questionComposition
  const compositionMatches = questions.length === interview.questionCount
    && actualComposition.MCQ_SINGLE === expectedComposition.mcqSingle
    && actualComposition.MCQ_MULTIPLE === expectedComposition.mcqMultiple
    && actualComposition.SHORT_TEXT === expectedComposition.shortText
    && actualComposition.LONG_TEXT === expectedComposition.longText

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
  useEffect(() => {
    if (draft.id) questionFormRef.current?.scrollIntoView({behavior: 'smooth', block: 'start'})
  }, [draft.id])

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
    setGenerating(true)
    notify('Generating questions through the AI Gateway…')
    try {
      await interviewApi.generateQuestions(interview.id)
      notify('AI questions generated and saved in PostgreSQL.')
      await loadQuestions()
    } catch (error) {
      notify(messageOf(error), true)
    } finally {
      setGenerating(false)
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
      <p><strong>Question mix:</strong> {expectedComposition.mcqSingle} single-answer MCQ,{' '}
        {expectedComposition.mcqMultiple} multiple-answer MCQ, {expectedComposition.shortText} short,{' '}
        {expectedComposition.longText} long</p>
      <p><strong>Passing score:</strong> {interview.passingPercentage}%</p>

      {showQuestions && questions.length > 0 && <div className="question-list">
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
        {interview.questionMode === 'DIRECT_LLM' && !draft.id &&
          <>
            <button type="button" disabled={generating} onClick={() => void generate()}>
              {generating ? 'Generating questions…' : 'Generate AI questions'}
            </button>
            {generating && <div className="generation-progress" role="progressbar"
              aria-label="Generating AI questions" aria-valuetext="Generation in progress">
              <div className="generation-progress-bar" />
              <span>AI Gateway is creating and validating the question set…</span>
            </div>}
          </>}
        {(interview.questionMode === 'MANUAL' || draft.id) &&
          <form ref={questionFormRef} className="question-builder" onSubmit={(event) => void saveQuestion(event)}>
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
        <button type="button" disabled={!compositionMatches} onClick={() => void publish()}>
          Publish {!compositionMatches && '(question mix incomplete)'}
        </button>
      </>}

      {showAssignment && interview.status === 'PUBLISHED' && <form className="assignment-form" onSubmit={(event) => void assign(event)}>
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
  const [activeView, setActiveView] = useState<'create' | 'drafts' | 'assign' | 'history'>('create')
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
        title: form.title,
        description: form.description,
        skills: form.technologies,
        difficulty: form.difficulty,
        questionMode: form.questionMode,
        durationMinutes: form.durationMinutes,
        questionCount: form.questionCount,
        passingPercentage: form.passingPercentage,
        questionComposition: {
          mcqSingle: form.mcqSingle,
          mcqMultiple: form.mcqMultiple,
          shortText: form.shortText,
          longText: form.longText,
        },
      })
      setForm(initialForm)
      notify('Draft interview created.')
      await load()
      setActiveView('drafts')
    } catch (error) {
      notify(messageOf(error), true)
    }
  }

  function setComposition(
    field: 'mcqSingle' | 'mcqMultiple' | 'shortText' | 'longText',
    value: number,
  ) {
    const next = {...form, [field]: Math.max(0, value)}
    next.questionCount = next.mcqSingle + next.mcqMultiple + next.shortText + next.longText
    setForm(next)
  }

  function setEcosystem(ecosystem: Ecosystem) {
    const firstTechnology = ecosystemTechnologies[ecosystem][0]
    setForm({...form, ecosystem, technologies: [firstTechnology]})
  }

  function toggleTechnology(technology: string) {
    const selected = form.technologies.includes(technology)
      ? form.technologies.filter((item) => item !== technology)
      : [...form.technologies, technology]
    setForm({...form, technologies: selected})
  }

  return (
    <main className="dashboard">
      <div className="dashboard-header">
        <div><p className="eyebrow">Interviewer workspace</p><h1>Interview management</h1></div>
        <button className="secondary-button" onClick={auth.logout}>Sign out</button>
      </div>
      {message && <p className={hasError ? 'error-message' : 'status-message'}>{message}</p>}
      <div className="workspace-layout">
        <nav className="workspace-nav" aria-label="Interviewer workspace">
          <button className={activeView === 'create' ? 'active' : ''} onClick={() => setActiveView('create')}>
            <span>1</span>Create interview draft
          </button>
          <button className={activeView === 'drafts' ? 'active' : ''} onClick={() => setActiveView('drafts')}>
            <span>2</span>Edit and publish
          </button>
          <button className={activeView === 'assign' ? 'active' : ''} onClick={() => setActiveView('assign')}>
            <span>3</span>Assign candidate
          </button>
          <button className={activeView === 'history' ? 'active' : ''} onClick={() => setActiveView('history')}>
            <span>4</span>Interview history
          </button>
          <button onClick={() => navigate('/interviewer/submissions')}>
            <span>5</span>Review submissions
          </button>
        </nav>

        <section className="workspace-content">
          {activeView === 'create' && <form className="form-grid" onSubmit={(event) => void create(event)}>
            <h2>Create interview draft</h2>
            <label>Title<input required value={form.title} onChange={(e) => setForm({...form, title: e.target.value})} /></label>
            <label>Description<textarea required value={form.description} onChange={(e) => setForm({...form, description: e.target.value})} /></label>
            <div className="inline-fields">
              <label>Ecosystem<select value={form.ecosystem}
                onChange={(e) => setEcosystem(e.target.value as Ecosystem)}>
                <option value="JAVA">Java ecosystem</option>
                <option value="PYTHON">Python ecosystem</option>
                <option value="UI">UI ecosystem</option>
                <option value="DATABASE">Database ecosystem</option>
                <option value="AI">AI ecosystem</option>
              </select></label>
              <div className="technology-field">
                <span>Technologies</span>
                <details className="technology-dropdown">
                  <summary>{form.technologies.length > 0
                    ? `${form.technologies.length} selected`
                    : 'Select technologies'}</summary>
                  <div className="technology-options">
                    {ecosystemTechnologies[form.ecosystem].map((technology) =>
                      <label key={technology}>
                        <input type="checkbox" checked={form.technologies.includes(technology)}
                          onChange={() => toggleTechnology(technology)} />
                        {technology}
                      </label>)}
                  </div>
                </details>
                <small>Select one or more technologies.</small>
                <div className="technology-description" role="status" aria-live="polite">
                  {form.technologies.length === 0
                    ? <span>Select a technology to see its description.</span>
                    : form.technologies.map((technology) =>
                      <div className="technology-description-item" key={technology}>
                        <strong>{technology}</strong>
                        <span>{technologyDescriptions[technology]}</span>
                        <span>AI-generated questions will include this technology.</span>
                      </div>)}
                </div>
              </div>
            </div>
            <fieldset className="question-composition">
              <legend>Types of questions</legend>
              <p>Choose how many questions of each type the interview should contain.</p>
              <div className="inline-fields">
                <label>MCQ (one answer)<input type="number" min="0" max="100" value={form.mcqSingle}
                  onChange={(e) => setComposition('mcqSingle', Number(e.target.value))} /></label>
                <label>MCQ (multiple answers)<input type="number" min="0" max="100" value={form.mcqMultiple}
                  onChange={(e) => setComposition('mcqMultiple', Number(e.target.value))} /></label>
                <label>Short answer (one line)<input type="number" min="0" max="100" value={form.shortText}
                  onChange={(e) => setComposition('shortText', Number(e.target.value))} /></label>
                <label>Long answer<input type="number" min="0" max="100" value={form.longText}
                  onChange={(e) => setComposition('longText', Number(e.target.value))} /></label>
              </div>
              <strong>Total questions: {form.questionCount}</strong>
              {form.questionCount === 0 && <span className="field-error"> Select at least one question.</span>}
              {form.questionCount > 100 && <span className="field-error"> Maximum 100 questions.</span>}
            </fieldset>
            <label>Difficulty<select value={form.difficulty} onChange={(e) => setForm({...form, difficulty: e.target.value})}><option>EASY</option><option>MEDIUM</option><option>HARD</option><option>MIXED</option></select></label>
            <label>Question mode<select value={form.questionMode} onChange={(e) => setForm({...form, questionMode: e.target.value})}><option>MANUAL</option><option>DIRECT_LLM</option></select></label>
            <label>Duration (minutes)<input type="number" min="5" max="480" value={form.durationMinutes} onChange={(e) => setForm({...form, durationMinutes: Number(e.target.value)})} /></label>
            <label>Passing percentage<input type="number" min="1" max="100" value={form.passingPercentage} onChange={(e) => setForm({...form, passingPercentage: Number(e.target.value)})} /></label>
            <button type="submit" disabled={form.technologies.length === 0
              || form.questionCount < 1 || form.questionCount > 100}>Create draft</button>
          </form>}

          {activeView === 'drafts' && <>
            <div className="section-heading"><h2>Edit and publish</h2><p>Complete questions, edit content, and publish ready drafts.</p></div>
            <div className="card-grid">
              {interviews.filter((interview) => interview.status === 'DRAFT').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates} notify={notify} reload={load} />)}
              {!interviews.some((interview) => interview.status === 'DRAFT') &&
                <p className="empty-state">No draft interviews. Create a draft to get started.</p>}
            </div>
          </>}

          {activeView === 'history' && <>
            <div className="section-heading"><h2>Interview history</h2><p>Read-only record of published interviews.</p></div>
            <div className="card-grid">
              {interviews.filter((interview) => interview.status !== 'DRAFT').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates}
                  notify={notify} reload={load} showAssignment={false} />)}
              {!interviews.some((interview) => interview.status !== 'DRAFT') &&
                <p className="empty-state">No published interviews yet.</p>}
            </div>
          </>}

          {activeView === 'assign' && <>
            <div className="section-heading"><h2>Assign candidate</h2><p>Schedule a candidate for any published interview, old or new.</p></div>
            <div className="card-grid">
              {interviews.filter((interview) => interview.status === 'PUBLISHED').map((interview) =>
                <InterviewCard key={interview.id} interview={interview} candidates={candidates}
                  notify={notify} reload={load} showQuestions={false} />)}
              {!interviews.some((interview) => interview.status === 'PUBLISHED') &&
                <p className="empty-state">No published interviews are available for assignment.</p>}
            </div>
          </>}
        </section>
      </div>
    </main>
  )
}
