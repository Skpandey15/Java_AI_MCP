import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { interviewApi } from '../api/interviewApi'
import { ecosystemLabels, ecosystemTechnologies, type Ecosystem } from './InterviewerDashboard'

const curatedTopics: Record<string, string[]> = {
  GraphQL: [
    'Schema and Type System', 'Queries and Variables', 'Mutations and Input Types',
    'Resolvers and Context', 'Strawberry GraphQL Fundamentals', 'Strawberry with Django',
    'Authentication and Authorization', 'DataLoader and N+1 Queries',
    'Subscriptions', 'Testing GraphQL APIs', 'Performance and Security',
  ],
  'Strawberry GraphQL': [
    'Schema and Object Types', 'Queries and Mutations', 'Resolvers',
    'Strawberry with Django', 'Django ORM Integration', 'Permissions and Authentication',
    'DataLoader', 'Subscriptions', 'Testing Strawberry APIs', 'Production Deployment',
  ],
  Django: [
    'Project Structure', 'Models and ORM', 'Views and URL Routing', 'Templates and Forms',
    'Django REST Framework', 'Strawberry GraphQL Integration', 'Authentication and Permissions',
    'Caching and Performance', 'Testing Django Applications', 'Production Deployment',
  ],
}

export function EducationPage() {
  const navigate = useNavigate()
  const ecosystems = useMemo(() => (Object.keys(ecosystemLabels) as Ecosystem[])
    .sort((a, b) => ecosystemLabels[a].localeCompare(ecosystemLabels[b])), [])
  const [ecosystem, setEcosystem] = useState<Ecosystem>('JAVA')
  const [technology, setTechnology] = useState<string>(ecosystemTechnologies.JAVA[0])
  const [topic, setTopic] = useState('')
  const [topics, setTopics] = useState<string[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    setBusy(true); setError(''); setTopic('')
    interviewApi.suggestTopics([technology], 'MEDIUM')
      .then(({ topics: loaded }) => setTopics(Array.from(new Set([
        ...(curatedTopics[technology] ?? []), ...loaded,
      ]))))
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Unable to load topics'))
      .finally(() => setBusy(false))
  }, [technology])

  function changeEcosystem(value: Ecosystem) {
    setEcosystem(value)
    setTechnology(ecosystemTechnologies[value][0])
  }

  function showDetails() {
    const params = new URLSearchParams({ ecosystem: ecosystemLabels[ecosystem], technology, topic })
    navigate(`/interviewer/education/details?${params}`)
  }

  return <main className="dashboard education-page">
    <div className="dashboard-header">
      <div><p className="eyebrow">Interviewer workspace · 6</p><h1>Educate Yourself</h1></div>
      <button className="secondary-button" onClick={() => navigate('/interviewer')}>Interview management</button>
    </div>
    <p className="summary">Choose a technology topic and build a structured zero-to-hero learning guide.</p>
    <section className="education-selector">
      <label>Ecosystem<select value={ecosystem} onChange={(event) => changeEcosystem(event.target.value as Ecosystem)}>
        {ecosystems.map((value) => <option key={value} value={value}>{ecosystemLabels[value]}</option>)}
      </select></label>
      <label>Technology<select value={technology} onChange={(event) => setTechnology(event.target.value)}>
        {ecosystemTechnologies[ecosystem].map((value) => <option key={value}>{value}</option>)}
      </select></label>
      <label>Topic<select value={topic} disabled={busy || topics.length === 0}
        onChange={(event) => setTopic(event.target.value)}>
        <option value="">{busy ? 'Loading topics…' : 'Select a topic'}</option>
        {topics.map((value) => <option key={value}>{value}</option>)}
      </select></label>
      <button disabled={!topic || busy} onClick={showDetails}>Show Details</button>
      {error && <p className="error-message" role="alert">{error}</p>}
    </section>
  </main>
}
