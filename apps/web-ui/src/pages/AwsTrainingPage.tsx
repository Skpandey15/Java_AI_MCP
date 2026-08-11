import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import {
  awsDays,
  awsPhases,
  contentTypeLabels,
  flattenTopics,
  type AwsContentType,
} from './awsTrainingContent'

export function AwsTrainingPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const auth = useAuth()
  const fromPath = (location.state as { from?: string } | null)?.from
  const dashboardPath = fromPath ?? (auth.roles.includes('interviewer') ? '/interviewer' : '/candidate')
  const isInterviewer = dashboardPath === '/interviewer'

  const [phaseId, setPhaseId] = useState(awsPhases[0].id)
  const [day, setDay] = useState(awsPhases[0].days[0])
  const [contentType, setContentType] = useState<AwsContentType>('theoretical')

  const phase = useMemo(() => awsPhases.find((p) => p.id === phaseId) ?? awsPhases[0], [phaseId])
  const content = contentType === 'theoretical' ? awsDays[day]?.theoretical : awsDays[day]?.practical
  const topics = content?.topics ?? []
  const hasTopics = flattenTopics(topics).length > 0
  const topicLabel = `${contentTypeLabels[contentType]} topics`

  function changePhase(nextId: number) {
    const next = awsPhases.find((p) => p.id === nextId) ?? awsPhases[0]
    setPhaseId(next.id)
    setDay(next.days[0]) // dependent dropdown resets to the first day of the new phase
  }

  // Selecting a topic opens an AI-generated details page for it (reuses the education flow).
  function openTopic(topic: string) {
    if (!topic) return
    const params = new URLSearchParams({
      ecosystem: 'AWS',
      technology: `AWS — ${phase.label}, Day ${day}`,
      topic,
      variant: 'guide',
      back: '/aws-training',
    })
    navigate(`/education/details?${params}`)
  }

  return (
    <main className="dashboard education-page aws-training-page">
      <div className="dashboard-header">
        <div>
          <p className="eyebrow">{isInterviewer ? 'Interviewer workspace' : 'Candidate workspace'} · AWS Training</p>
          <h1>AWS Zero to Production Hero — 15-Day Program</h1>
        </div>
        <button className="secondary-button" onClick={() => navigate(dashboardPath)}>
          {isInterviewer ? 'Interview management' : 'Back to my interviews'}
        </button>
      </div>
      <p className="summary">
        {phase.label} · {phase.focus}. Choose a phase, day and content type, then pick a topic to
        open an AI-generated deep-dive.
      </p>

      <section className="education-selector aws-selector">
        <label>
          Phase
          <select value={phaseId} onChange={(e) => changePhase(Number(e.target.value))}>
            {awsPhases.map((p) => <option key={p.id} value={p.id}>{p.label}</option>)}
          </select>
        </label>
        <label>
          Day
          <select value={day} onChange={(e) => setDay(Number(e.target.value))}>
            {phase.days.map((d) => <option key={d} value={d}>Day {d}</option>)}
          </select>
        </label>
        <label>
          Content Type
          <select value={contentType} onChange={(e) => setContentType(e.target.value as AwsContentType)}>
            {(Object.keys(contentTypeLabels) as AwsContentType[]).map((t) => (
              <option key={t} value={t}>{contentTypeLabels[t]}</option>
            ))}
          </select>
        </label>
        <label>
          {topicLabel}
          <select
            value=""
            disabled={!hasTopics}
            onChange={(e) => openTopic(e.target.value)}
          >
            <option value="">{hasTopics ? 'Select a topic…' : 'Topics coming soon'}</option>
            {topics.map((entry, i) =>
              typeof entry === 'string' ? (
                <option key={`t-${i}`} value={entry}>{entry}</option>
              ) : (
                <optgroup key={`g-${i}`} label={entry.group}>
                  {entry.items.map((item) => <option key={item} value={item}>{item}</option>)}
                </optgroup>
              ),
            )}
          </select>
        </label>
      </section>

      {content && (
        <p className="aws-day-title">
          <strong>Day {day} · {contentTypeLabels[contentType]}:</strong> {content.title}
        </p>
      )}
    </main>
  )
}
