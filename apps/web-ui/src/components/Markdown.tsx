import { type JSX, type ReactNode } from 'react'

// Minimal, dependency-free, XSS-safe Markdown renderer. Builds React elements
// directly (never dangerouslySetInnerHTML) and only follows explicit http(s)
// links. Supports the subset the coaching agent emits: ## / ### headings,
// - / * bullet lists, 1. ordered lists, **bold**, `code`, and [text](url).
function inline(text: string): ReactNode[] {
  const nodes: ReactNode[] = []
  const re = /\*\*([^*]+)\*\*|`([^`]+)`|\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g
  let last = 0
  let key = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(text))) {
    if (m.index > last) nodes.push(text.slice(last, m.index))
    if (m[1]) nodes.push(<strong key={key++}>{m[1]}</strong>)
    else if (m[2]) nodes.push(<code key={key++}>{m[2]}</code>)
    else if (m[3]) nodes.push(
      <a key={key++} href={m[4]} target="_blank" rel="noopener noreferrer">{m[3]}</a>)
    last = m.index + m[0].length
  }
  if (last < text.length) nodes.push(text.slice(last))
  return nodes
}

const SPECIAL = /^(#{1,4})\s|^\s*[-*]\s|^\s*\d+\.\s/

export function Markdown({ content, className }: { content: string; className?: string }) {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let i = 0
  let key = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }

    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      const Tag = `h${Math.min(heading[1].length + 1, 6)}` as keyof JSX.IntrinsicElements
      blocks.push(<Tag key={key++}>{inline(heading[2])}</Tag>)
      i++
      continue
    }

    if (/^\s*[-*]\s+/.test(line)) {
      const items: ReactNode[] = []
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
        items.push(<li key={key++}>{inline(lines[i].replace(/^\s*[-*]\s+/, ''))}</li>)
        i++
      }
      blocks.push(<ul key={key++}>{items}</ul>)
      continue
    }

    if (/^\s*\d+\.\s+/.test(line)) {
      const items: ReactNode[] = []
      while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
        items.push(<li key={key++}>{inline(lines[i].replace(/^\s*\d+\.\s+/, ''))}</li>)
        i++
      }
      blocks.push(<ol key={key++}>{items}</ol>)
      continue
    }

    const para: string[] = []
    while (i < lines.length && lines[i].trim() && !SPECIAL.test(lines[i])) {
      para.push(lines[i])
      i++
    }
    blocks.push(<p key={key++}>{inline(para.join(' '))}</p>)
  }
  return <div className={className}>{blocks}</div>
}
