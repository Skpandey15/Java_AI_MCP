import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { interviewApi, type TopicDetails } from '../api/interviewApi'
import { Markdown } from '../components/Markdown'

export function TopicDetailsPage() {
  const navigate = useNavigate()
  const [params] = useSearchParams()
  const ecosystem = params.get('ecosystem') ?? ''
  const technology = params.get('technology') ?? ''
  const topic = params.get('topic') ?? ''
  const [details, setDetails] = useState<TopicDetails>()
  const [error, setError] = useState('')
  const [elapsedSeconds, setElapsedSeconds] = useState(0)

  useEffect(() => {
    if (!ecosystem || !technology || !topic) { setError('Select a topic first.'); return }
    interviewApi.topicDetails(ecosystem, technology, topic)
      .then(setDetails)
      .catch((reason: unknown) => setError(reason instanceof Error ? reason.message : 'Unable to generate guide'))
  }, [ecosystem, technology, topic])

  useEffect(() => {
    if (details || error) return
    const startedAt = Date.now()
    const timer = window.setInterval(() => {
      setElapsedSeconds(Math.floor((Date.now() - startedAt) / 1000))
    }, 1000)
    return () => window.clearInterval(timer)
  }, [details, error])

  const progress = Math.min(92, 10 + elapsedSeconds * 1.35)
  const stage = elapsedSeconds < 8
    ? 'Connecting to the learning assistant'
    : elapsedSeconds < 25
      ? 'Planning the learning path and prerequisites'
      : elapsedSeconds < 50
        ? 'Writing examples, production patterns, and exercises'
        : 'Reviewing and formatting your complete guide'

  function safeFileName() {
    return `${technology}-${topic}-zero-to-hero`.replace(/[^a-z0-9]+/gi, '-').replace(/^-|-$/g, '')
  }

  function exportPdf() {
    window.print()
  }

  function exportWord() {
    if (!details) return
    const guide = document.querySelector('.learning-guide')?.innerHTML ?? ''
    const html = `<!doctype html><html><head><meta charset="utf-8"><title>${details.title}</title>
      <style>body{font-family:Arial,sans-serif;line-height:1.6;color:#172b4d;margin:40px}
      h1,h2,h3{color:#124f98}pre{white-space:pre-wrap;padding:12px;background:#f1f5f9}
      code{font-family:Consolas,monospace}table{border-collapse:collapse;width:100%}
      th,td{border:1px solid #cbd5e1;padding:8px;text-align:left}</style></head><body>
      <h1>${details.title}</h1><p><strong>${ecosystem} · ${technology} · ${topic}</strong></p>
      ${guide}</body></html>`
    const url = URL.createObjectURL(new Blob(['\ufeff', html], { type: 'application/msword' }))
    const link = document.createElement('a')
    link.href = url; link.download = `${safeFileName()}.doc`; link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }

  return <main className="dashboard topic-details-page">
    <div className="dashboard-header">
      <div><p className="eyebrow">{ecosystem} · {technology}</p><h1>{details?.title ?? topic}</h1></div>
      <div className="topic-export-actions">
        <button disabled={!details} onClick={exportPdf}>Export to PDF</button>
        <button disabled={!details} onClick={exportWord}>Export to MS Word</button>
        <button className="secondary-button" onClick={() => navigate('/education')}>Choose another topic</button>
      </div>
    </div>
    {!details && !error && <section className="learning-loading" aria-live="polite" aria-busy="true">
      <div className="learning-loading-heading"><span className="learning-spinner" aria-hidden="true" />
        <div><h2>Building your zero-to-hero guide…</h2><p>{stage}</p></div>
      </div>
      <div className="learning-progress" role="progressbar" aria-label="Guide generation progress"
        aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(progress)}>
        <span style={{ width: `${progress}%` }} />
      </div>
      <div className="learning-progress-meta"><span>{Math.round(progress)}% prepared</span>
        <span>{elapsedSeconds}s elapsed</span></div>
      <ol className="learning-stages">
        <li className={elapsedSeconds >= 0 ? 'active' : ''}>Analyze topic</li>
        <li className={elapsedSeconds >= 8 ? 'active' : ''}>Plan curriculum</li>
        <li className={elapsedSeconds >= 25 ? 'active' : ''}>Create guide</li>
        <li className={elapsedSeconds >= 50 ? 'active' : ''}>Quality review</li>
      </ol>
      <p className="learning-wait-note">AI generation usually takes 30–90 seconds. Please keep this page open.</p>
    </section>}
    {error && <p className="error-message" role="alert">{error}</p>}
    {details && <article className="learning-guide"><Markdown content={details.content} /></article>}
  </main>
}
