import { type JSX, type ReactNode } from 'react'

// Minimal, dependency-free, XSS-safe Markdown renderer. Builds React elements
// directly (never dangerouslySetInnerHTML) and only follows explicit http(s)
// links. Supports headings, lists, fenced code, tables, blockquotes, separators,
// bold, inline code, and explicit http(s) links.
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

const SPECIAL = /^(#{1,4})\s|^\s*[-*]\s|^\s*\d+\.\s|^```|^>\s?|^\s*---+\s*$/

function tableCells(line: string) {
  return line.trim().replace(/^\||\|$/g, '').split('|').map((cell) => cell.trim())
}

export function Markdown({ content, className }: { content: string; className?: string }) {
  const lines = content.replace(/\r\n/g, '\n').split('\n')
  const blocks: ReactNode[] = []
  let i = 0
  let key = 0
  while (i < lines.length) {
    const line = lines[i]
    if (!line.trim()) { i++; continue }

    const fence = /^```([^\s`]*)\s*$/.exec(line.trim())
    if (fence) {
      i++
      const code: string[] = []
      while (i < lines.length && !/^```\s*$/.test(lines[i].trim())) code.push(lines[i++])
      if (i < lines.length) i++
      blocks.push(<pre key={key++}><code className={fence[1] ? `language-${fence[1]}` : undefined}>
        {code.join('\n')}
      </code></pre>)
      continue
    }

    if (/^\s*---+\s*$/.test(line)) {
      blocks.push(<hr key={key++} />); i++; continue
    }

    if (/^>\s?/.test(line)) {
      const quote: string[] = []
      while (i < lines.length && /^>\s?/.test(lines[i])) quote.push(lines[i++].replace(/^>\s?/, ''))
      blocks.push(<blockquote key={key++}>{inline(quote.join(' '))}</blockquote>)
      continue
    }

    if (line.includes('|') && i + 1 < lines.length
        && /^\s*\|?\s*:?-{3,}/.test(lines[i + 1])) {
      const headers = tableCells(line); i += 2
      const rows: string[][] = []
      while (i < lines.length && lines[i].includes('|') && lines[i].trim()) rows.push(tableCells(lines[i++]))
      blocks.push(<div className="markdown-table-wrap" key={key++}><table>
        <thead><tr>{headers.map((cell) => <th key={key++}>{inline(cell)}</th>)}</tr></thead>
        <tbody>{rows.map((row) => <tr key={key++}>{row.map((cell) =>
          <td key={key++}>{inline(cell)}</td>)}</tr>)}</tbody>
      </table></div>)
      continue
    }

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
