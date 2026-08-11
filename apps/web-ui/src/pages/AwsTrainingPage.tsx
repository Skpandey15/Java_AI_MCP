import { useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthProvider'
import { Mermaid } from '../components/Mermaid'
import {
  awsDays,
  awsPhases,
  contentTypeLabels,
  type AwsBlock,
  type AwsContentType,
  type AwsDayContent,
} from './awsTrainingContent'

function McqItem(
  { index, nameBase, q, options, answer }:
  { index: number; nameBase: string; q: string; options: string[]; answer: number },
) {
  const [choice, setChoice] = useState<number | null>(null)
  // Unique per question (and per block/day/content type) so radio groups never merge across
  // questions — nameBase already encodes day + content type + block index.
  const name = `mcq-${nameBase}-${index}`
  return (
    <li className="aws-mcq">
      <fieldset className="aws-mcq-fieldset">
        <legend className="aws-mcq-q"><strong>{index + 1}.</strong> {q}</legend>
        <ul className="aws-mcq-options">
          {options.map((option, i) => {
            const revealed = choice !== null
            const state = revealed && i === answer ? ' correct' : revealed && i === choice ? ' wrong' : ''
            return (
              <li key={i} className={`aws-mcq-option${state}`}>
                <label>
                  <input type="radio" name={name} checked={choice === i} onChange={() => setChoice(i)} />
                  {option}
                </label>
              </li>
            )
          })}
        </ul>
        {choice !== null && (
          <p className="aws-mcq-result" role="status">
            {choice === answer ? 'Correct.' : `Not quite — the answer is "${options[answer]}".`}
          </p>
        )}
      </fieldset>
    </li>
  )
}

function Block({ block, keyBase }: { block: AwsBlock; keyBase: string }) {
  switch (block.kind) {
    case 'lead':
      return <p className="summary">{block.text}</p>
    case 'objectives':
      return (
        <section className="aws-block">
          <h3>Learning objectives</h3>
          <ul className="aws-list">{block.items.map((t, i) => <li key={i}>{t}</li>)}</ul>
        </section>
      )
    case 'topics':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ul className="aws-topics">{block.items.map((t, i) => <li key={i}>{t}</li>)}</ul>
        </section>
      )
    case 'diagram':
      return (
        <section className="aws-block">
          <h3>Architecture diagram</h3>
          <Mermaid code={block.code} />
          {block.caption && <p className="aws-caption">{block.caption}</p>}
        </section>
      )
    case 'callout':
      return (
        <section className={`aws-callout aws-callout-${block.tone ?? 'info'}`}>
          <h3>{block.heading}</h3>
          <ul className="aws-list">{block.items.map((t, i) => <li key={i}>{t}</li>)}</ul>
        </section>
      )
    case 'steps':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ol className="aws-steps">{block.items.map((t, i) => <li key={i}>{t}</li>)}</ol>
        </section>
      )
    case 'checklist':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ul className="aws-checklist">
            {block.items.map((t, i) => (
              <li key={i}><label><input type="checkbox" />{t}</label></li>
            ))}
          </ul>
        </section>
      )
    case 'code':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <pre className="aws-code"><code>{block.code}</code></pre>
        </section>
      )
    case 'scenarios':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ul className="aws-list">{block.items.map((t, i) => <li key={i}>{t}</li>)}</ul>
        </section>
      )
    case 'mcqs':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ol className="aws-mcqs">
            {block.items.map((m, i) => (
              <McqItem key={`${keyBase}-${i}`} index={i} nameBase={keyBase} q={m.q} options={m.options} answer={m.answer} />
            ))}
          </ol>
        </section>
      )
    case 'interview':
      return (
        <section className="aws-callout aws-callout-info">
          {block.heading && <h3>{block.heading}</h3>}
          <p className="aws-mcq-q"><strong>Q.</strong> {block.q}</p>
          <details className="aws-interview">
            <summary>Show model answer</summary>
            <p>{block.a}</p>
          </details>
        </section>
      )
    case 'links':
      return (
        <section className="aws-block">
          {block.heading && <h3>{block.heading}</h3>}
          <ul className="aws-links">
            {block.items.map((l, i) => (
              <li key={i}><a href={l.href} target="_blank" rel="noreferrer noopener">{l.label}</a></li>
            ))}
          </ul>
        </section>
      )
    case 'deliverable':
      return (
        <section className="aws-deliverable">
          <h3>Deliverable</h3>
          <p>{block.text}</p>
        </section>
      )
    default:
      return null
  }
}

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

  function changePhase(nextId: number) {
    const next = awsPhases.find((p) => p.id === nextId) ?? awsPhases[0]
    setPhaseId(next.id)
    setDay(next.days[0]) // dependent dropdown resets to the first day of the new phase
  }

  const content: AwsDayContent | undefined =
    contentType === 'theoretical' ? awsDays[day]?.theoretical : awsDays[day]?.practical

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
      <p className="summary">{phase.label} · {phase.focus}</p>

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
      </section>

      {content ? (
        <article className="aws-content">
          <header className="aws-content-head">
            <p className="eyebrow">Day {day} · {contentTypeLabels[contentType]}</p>
            <h2>{content.title}</h2>
          </header>
          {content.blocks.map((block, i) => (
            <Block key={`${day}-${contentType}-${i}`} block={block} keyBase={`${day}-${contentType}-${i}`} />
          ))}
        </article>
      ) : (
        <p className="summary">No content available for this selection.</p>
      )}
    </main>
  )
}
