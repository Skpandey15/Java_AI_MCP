import { useEffect, useRef, useState } from 'react'

// Lazy-loads mermaid only when a diagram is actually present, so it never weighs down the
// main bundle. Renders the diagram to sanitized SVG; on any failure (invalid syntax, a CSP
// that blocks the library, load error) it falls back to showing the diagram source as text,
// so the page is always readable.

type MermaidApi = { initialize: (c: object) => void; render: (id: string, src: string) =>
  Promise<{ svg: string }> }

let mermaidPromise: Promise<MermaidApi> | null = null
function loadMermaid(): Promise<MermaidApi> {
  if (!mermaidPromise) {
    mermaidPromise = import('mermaid').then((mod) => {
      const api = mod.default as unknown as MermaidApi
      api.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'neutral' })
      return api
    })
  }
  return mermaidPromise
}

let seq = 0

export function Mermaid({ code }: { code: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    setFailed(false)
    const id = `mermaid-svg-${seq++}`
    loadMermaid()
      .then((mermaid) => mermaid.render(id, code))
      .then(({ svg }) => {
        if (cancelled || !ref.current) return
        // `svg` is mermaid's own output, produced with securityLevel:'strict' which sanitizes it
        // through DOMPurify; the diagram source itself comes from our AI service, not from end
        // users. Assigning that sanitized SVG is the standard, safe mermaid integration.
        ref.current.innerHTML = svg // nosemgrep
      })
      .catch(() => { if (!cancelled) setFailed(true) })
    return () => { cancelled = true }
  }, [code])

  if (failed) {
    return <pre className="mermaid-fallback"><code>{code}</code></pre>
  }
  return <div className="mermaid-diagram" role="img" aria-label="Architecture diagram" ref={ref} />
}
