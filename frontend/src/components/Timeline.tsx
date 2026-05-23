import { useState } from 'react'
import type { AgentEvent } from '../types'

type ToolCall = Extract<AgentEvent, { type: 'tool_call' }>
type ToolResult = Extract<AgentEvent, { type: 'tool_result' }>
type Step = ToolCall | ToolResult
type IterGroup = { iteration: number; steps: Step[] }
type Terminal = Extract<AgentEvent, { type: 'done' }> | Extract<AgentEvent, { type: 'error' }>

type Verdict = 'ESCALATE' | 'REVIEW' | 'CLEAR'
type Suspicion = 'HIGH' | 'MEDIUM' | 'LOW'

interface Finding {
  orig_id: string
  dup_id: string
  user_id: string
  ticker: string
  side: string
  qty: string | number
  price: string | number
  gap_s: number
  suspicion: Suspicion
}

interface FindingsJson {
  verdict: Verdict
  total_orders: number
  pairs: Finding[]
}

const VERDICT_STYLE: Record<Verdict, { banner: string; label: string }> = {
  ESCALATE: { banner: 'bg-[#7b2d26]/10 border-[#7b2d26]/30', label: 'text-[#7b2d26]' },
  REVIEW:   { banner: 'bg-[#c47d0e]/10 border-[#c47d0e]/30', label: 'text-[#c47d0e]' },
  CLEAR:    { banner: 'bg-[#0b7a75]/10 border-[#0b7a75]/30', label: 'text-[#0b7a75]' },
}

const SUSPICION_BADGE: Record<Suspicion, string> = {
  HIGH:   'bg-[#7b2d26] text-white',
  MEDIUM: 'bg-[#c47d0e] text-white',
  LOW:    'bg-[#19535f] text-white',
}

function shortId(id: string) {
  return id.slice(0, 8) + '…'
}

function shortUser(id: string) {
  return '…' + id.slice(-4)
}

function ResultPanel({ answer }: { answer: string }) {
  const jsonMatch = answer.match(/<findings_json>([\s\S]*?)<\/findings_json>/)
  let parsed: FindingsJson | null = null
  try {
    if (jsonMatch) parsed = JSON.parse(jsonMatch[1].trim())
  } catch {}

  const findings = parsed?.pairs ?? null
  const verdict = parsed?.verdict
  const totalOrders = parsed?.total_orders

  const narrative = answer.replace(/<findings_json>[\s\S]*?<\/findings_json>/, '').trim()

  // Repeat offenders from findings data
  const userPairCount: Record<string, number> = {}
  if (findings) {
    for (const f of findings) {
      userPairCount[f.user_id] = (userPairCount[f.user_id] ?? 0) + 1
    }
  }
  const repeats = Object.entries(userPairCount).filter(([, n]) => n > 1)

  if (findings) {
    const vs = verdict ? VERDICT_STYLE[verdict] : null
    return (
      <div className="space-y-3">

        {/* Verdict banner */}
        {verdict && vs && (
          <div className={`flex items-center gap-3 px-4 py-3 rounded-md border ${vs.banner}`}>
            <span className={`text-sm font-bold font-mono tracking-widest ${vs.label}`}>{verdict}</span>
            <span className="text-zinc-300">·</span>
            {totalOrders != null && <span className="text-xs text-zinc-500 font-mono">{totalOrders} orders scanned</span>}
            {findings.length > 0 && (
              <>
                <span className="text-zinc-300">·</span>
                <span className="text-xs text-zinc-500 font-mono">{findings.length} pairs</span>
                <span className="text-zinc-300">·</span>
                <span className="text-xs text-zinc-500 font-mono">
                  {findings.filter(f => f.suspicion === 'HIGH').length} HIGH
                  {findings.filter(f => f.suspicion === 'MEDIUM').length > 0 && `, ${findings.filter(f => f.suspicion === 'MEDIUM').length} MEDIUM`}
                  {findings.filter(f => f.suspicion === 'LOW').length > 0 && `, ${findings.filter(f => f.suspicion === 'LOW').length} LOW`}
                </span>
              </>
            )}
          </div>
        )}

        {/* Findings table */}
        {findings.length > 0 ? (
          <div className="rounded-md border border-zinc-200 overflow-hidden">
            <table className="w-full text-xs border-collapse">
              <thead>
                <tr className="bg-zinc-50 border-b border-zinc-200">
                  {['Suspicion', 'Ticker', 'Side', 'Qty', 'Price', 'Gap', 'User', 'Original', 'Duplicate'].map(h => (
                    <th key={h} className="text-left px-3 py-2 text-[10px] font-bold text-zinc-500 uppercase tracking-wide whitespace-nowrap">
                      {h}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {findings.map((f, i) => (
                  <tr key={i} className={`border-b border-zinc-100 last:border-0 ${i % 2 === 1 ? 'bg-zinc-50/60' : 'bg-white'}`}>
                    <td className="px-3 py-2">
                      <span className={`inline-block px-1.5 py-0.5 rounded-sm text-[9px] font-bold font-mono tracking-widest ${SUSPICION_BADGE[f.suspicion]}`}>
                        {f.suspicion}
                      </span>
                    </td>
                    <td className="px-3 py-2 font-mono font-semibold text-zinc-800">{f.ticker}</td>
                    <td className="px-3 py-2 font-mono text-zinc-600">{f.side}</td>
                    <td className="px-3 py-2 font-mono text-zinc-600">{f.qty}</td>
                    <td className="px-3 py-2 font-mono text-zinc-600">${f.price}</td>
                    <td className="px-3 py-2 font-mono text-zinc-500">{f.gap_s.toFixed(2)}s</td>
                    <td className="px-3 py-2 font-mono text-zinc-400 text-[10px]" title={f.user_id}>{shortUser(f.user_id)}</td>
                    <td className="px-3 py-2 font-mono text-zinc-400 text-[10px]" title={f.orig_id}>{shortId(f.orig_id)}</td>
                    <td className="px-3 py-2 font-mono text-zinc-400 text-[10px]" title={f.dup_id}>{shortId(f.dup_id)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <div className="rounded-md border border-zinc-200 bg-white px-4 py-3">
            <p className="text-sm text-zinc-500 font-mono">No duplicate pairs found.</p>
          </div>
        )}

        {/* Repeat offenders */}
        {repeats.length > 0 && (
          <div className="rounded-md border border-zinc-200 bg-white px-4 py-3">
            <p className="text-[10px] font-bold text-zinc-500 uppercase tracking-wide mb-2">Repeat Offenders</p>
            <div className="flex flex-col gap-1">
              {repeats.map(([uid, count]) => (
                <div key={uid} className="flex items-center gap-2">
                  <span className="font-mono text-[11px] text-zinc-700" title={uid}>{shortUser(uid)}</span>
                  <span className="text-[10px] text-zinc-400">{count} pairs</span>
                </div>
              ))}
            </div>
          </div>
        )}
      </div>
    )
  }

  // Fallback: render narrative text with basic formatting
  return <NarrativeText text={narrative} />
}

function NarrativeText({ text }: { text: string }) {
  const lines = text.split('\n')
  return (
    <div className="space-y-1 text-sm leading-relaxed">
      {lines.map((line, i) => {
        if (/^# /.test(line))  return <p key={i} className="text-base font-bold text-zinc-900 mt-2">{line.slice(2)}</p>
        if (/^## /.test(line)) return <p key={i} className="text-sm font-bold text-zinc-800 mt-2">{line.slice(3)}</p>
        if (/^### /.test(line)) return <p key={i} className="text-xs font-bold text-zinc-700 uppercase tracking-wide mt-2">{line.slice(4)}</p>
        if (line.trim() === '') return <div key={i} className="h-1" />
        // inline bold: **text**
        const parts = line.split(/(\*\*[^*]+\*\*)/)
        return (
          <p key={i} className="text-zinc-700">
            {parts.map((part, j) =>
              /^\*\*/.test(part)
                ? <strong key={j} className="font-semibold text-zinc-900">{part.slice(2, -2)}</strong>
                : part
            )}
          </p>
        )
      })}
    </div>
  )
}

function groupEvents(events: AgentEvent[]) {
  const iterations: IterGroup[] = []
  let terminal: Terminal | null = null

  for (const e of events) {
    if (e.type === 'iteration_start') {
      iterations.push({ iteration: e.iteration, steps: [] })
    } else if (e.type === 'tool_call' || e.type === 'tool_result') {
      const last = iterations[iterations.length - 1]
      if (last) last.steps.push(e)
    } else if (e.type === 'done' || e.type === 'error') {
      terminal = e
    }
  }

  return { iterations, terminal }
}

function JsonBlock({ value }: { value: string | Record<string, unknown> }) {
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  return (
    <pre className="mt-2 rounded-sm bg-[#f0f3f5] border border-[#d7c9aa] p-3 text-[11px] text-zinc-600 overflow-x-auto whitespace-pre-wrap break-words max-h-60 overflow-y-auto font-mono leading-relaxed">
      {text}
    </pre>
  )
}

function StepRow({ step, isLast }: { step: Step; isLast: boolean }) {
  const [open, setOpen] = useState(false)
  const isCall = step.type === 'tool_call'

  return (
    <div className="mb-1.5">
      <div className="flex items-center gap-2">
        <span className="text-[#d7c9aa] font-mono select-none text-[11px] leading-none">{isLast ? '└─' : '├─'}</span>
        {isCall ? (
          <code className="text-[#19535f] bg-[#d7c9aa]/20 border border-[#d7c9aa] px-1.5 py-0.5 rounded-sm font-mono text-[11px]">
            {(step as ToolCall).name}
          </code>
        ) : (
          <>
            <span className="text-[#0b7a75] text-[11px]">✓</span>
            <span className="text-zinc-500 font-mono text-[11px]">{(step as ToolResult).name}</span>
          </>
        )}
        <button
          onClick={() => setOpen(o => !o)}
          className="text-[10px] text-zinc-400 hover:text-[#0b7a75] border border-zinc-200 hover:border-[#0b7a75] rounded-sm px-1.5 py-0.5 transition-colors font-mono bg-white"
        >
          {open ? '▾ hide' : `▸ ${isCall ? 'input' : 'result'}`}
        </button>
      </div>
      {open && (
        <div className="ml-7 mt-0.5">
          <JsonBlock value={isCall ? (step as ToolCall).input : (step as ToolResult).result} />
        </div>
      )}
    </div>
  )
}

export function Timeline({ events }: { events: AgentEvent[] }) {
  const { iterations, terminal } = groupEvents(events)
  if (iterations.length === 0 && !terminal) return null

  const lineFollows = (gi: number) => gi < iterations.length - 1 || terminal !== null

  return (
    <div>
      {iterations.map((group, gi) => (
        <div key={group.iteration} className="flex">
          {/* Rail column */}
          <div className="flex flex-col items-center w-6 shrink-0 mr-4">
            <div className="w-5 h-5 rounded-full border border-[#0b7a75]/40 bg-white flex items-center justify-center shrink-0 z-10">
              <span className="text-[9px] font-semibold text-[#19535f] leading-none font-mono">{group.iteration}</span>
            </div>
            {lineFollows(gi) && <div className="w-px flex-1 bg-[#d7c9aa] my-1" />}
          </div>
          {/* Content */}
          <div className={`flex-1 min-w-0 ${lineFollows(gi) ? 'pb-4' : 'pb-1'}`}>
            <p className="text-[10px] font-semibold text-[#0b7a75]/70 uppercase tracking-widest mb-2 font-mono">
              Step {group.iteration}
            </p>
            {group.steps.length > 0
              ? group.steps.map((step, si) => (
                  <StepRow key={si} step={step} isLast={si === group.steps.length - 1} />
                ))
              : <span className="text-[11px] text-zinc-400 italic">thinking…</span>
            }
          </div>
        </div>
      ))}

      {terminal && (
        <div className="flex">
          {/* Terminal node */}
          <div className="w-6 shrink-0 mr-4 pt-0.5">
            {terminal.type === 'done' ? (
              <div className="w-5 h-5 rounded-full border border-[#0b7a75] bg-white flex items-center justify-center">
                <span className="text-[10px] text-[#19535f] leading-none">★</span>
              </div>
            ) : (
              <div className="w-5 h-5 rounded-full border border-[#7b2d26] bg-white flex items-center justify-center">
                <span className="text-[10px] text-[#7b2d26] leading-none">✕</span>
              </div>
            )}
          </div>
          {/* Terminal content */}
          <div className="flex-1 min-w-0 pb-2">
            {terminal.type === 'done' ? (
              <>
                <p className="text-[10px] font-bold text-zinc-600 uppercase tracking-wide mb-3">Result</p>
                <ResultPanel answer={terminal.answer} />
              </>
            ) : (
              <>
                <p className="text-[10px] font-bold text-[#7b2d26] uppercase tracking-wide mb-1">Error</p>
                <p className="text-sm text-[#7b2d26]">{terminal.message}</p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
