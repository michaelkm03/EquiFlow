import { useState, Fragment, type ReactNode } from 'react'
import type { AgentEvent } from '../types'

// ── Event grouping types ───────────────────────────────────────────────────────
type ToolCall   = Extract<AgentEvent, { type: 'tool_call' }>
type ToolResult = Extract<AgentEvent, { type: 'tool_result' }>
type Step       = ToolCall | ToolResult
type IterGroup  = { iteration: number; steps: Step[] }
type Terminal   = Extract<AgentEvent, { type: 'done' }> | Extract<AgentEvent, { type: 'error' }>

// ── findings_json schemas ──────────────────────────────────────────────────────

// Duplicate
type Suspicion = 'HIGH' | 'MEDIUM' | 'LOW'
interface DuplicateFinding {
  orig_id: string; dup_id: string; user_id: string; ticker: string
  side: string; qty: string | number; price: string | number
  gap_s: number; suspicion: Suspicion
}
interface DuplicateFindings {
  verdict: 'ESCALATE' | 'REVIEW' | 'CLEAR'
  total_orders: number
  pairs: DuplicateFinding[]
}

// Escalation
type EscalationVerdict  = 'RETRY' | 'NO_ACTION' | 'INVESTIGATE' | 'ESCALATE' | 'FLAG_SYSTEMIC'
type EscalationPriority = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'NONE'
type OverallVerdict     = 'ALL_CLEAR' | 'ESCALATE' | 'FLAG_SYSTEMIC'
interface EscalationRow {
  order_id: string; user_id: string; ticker: string; failed_step: string
  failure_reason: string; saga_status: string; retry_count: number
  systemic_risk: boolean; verdict: EscalationVerdict; priority: EscalationPriority
  confidence: number; evidence: string[]; recommended_actions: string[]
  incident_id: string | null
}
interface EscalationFindings {
  mode: string
  window: { from: string; to: string }
  total_investigated: number
  verdicts: EscalationRow[]
  overall_verdict: OverallVerdict
}

// Compliance
type ComplianceVerdict = 'CLEAR' | 'REVIEW' | 'ESCALATE'
interface ComplianceOffender { user_id: string; breach_count: number; latest_breach: string; types: string[] }
interface ComplianceFindings {
  period: { from: string; to: string }
  total_breaches: number
  violation_breakdown: Record<string, number>
  repeat_offenders: ComplianceOffender[]
  verdict: ComplianceVerdict
}

// Triage
type TriageRecommendation = 'RETRY' | 'ESCALATE' | 'INVESTIGATE' | 'NO_ACTION'
interface TriageFindings {
  order_id: string
  ticker: string
  status: string
  user_id: string
  saga_status: string
  failed_step: string
  failure_reason: string
  retry_count: number
  recommendation: TriageRecommendation
  reasoning: string
}

// ── Style maps ─────────────────────────────────────────────────────────────────
const BANNER_STYLE: Record<string, { banner: string; label: string }> = {
  ESCALATE:     { banner: 'bg-[#7b2d26]/10 border-[#7b2d26]/30', label: 'text-[#7b2d26]' },
  FLAG_SYSTEMIC:{ banner: 'bg-[#c47d0e]/10 border-[#c47d0e]/30', label: 'text-[#c47d0e]' },
  REVIEW:       { banner: 'bg-[#c47d0e]/10 border-[#c47d0e]/30', label: 'text-[#c47d0e]' },
  CLEAR:        { banner: 'bg-[#0b7a75]/10 border-[#0b7a75]/30', label: 'text-[#0b7a75]' },
  ALL_CLEAR:    { banner: 'bg-[#0b7a75]/10 border-[#0b7a75]/30', label: 'text-[#0b7a75]' },
}

const SUSPICION_BADGE: Record<Suspicion, string> = {
  HIGH:   'bg-[#7b2d26] text-white',
  MEDIUM: 'bg-[#c47d0e] text-white',
  LOW:    'bg-[#19535f] text-white',
}

const ESC_VERDICT_BADGE: Record<EscalationVerdict, string> = {
  ESCALATE:     'bg-[#7b2d26] text-white',
  FLAG_SYSTEMIC:'bg-[#c47d0e] text-white',
  INVESTIGATE:  'bg-zinc-500 text-white',
  RETRY:        'bg-[#19535f] text-white',
  NO_ACTION:    'bg-zinc-200 text-zinc-600',
}

const ESC_PRIORITY_BADGE: Record<EscalationPriority, string> = {
  CRITICAL: 'bg-[#7b2d26]/15 text-[#7b2d26] border border-[#7b2d26]/30',
  HIGH:     'bg-[#c47d0e]/15 text-[#c47d0e] border border-[#c47d0e]/30',
  MEDIUM:   'bg-zinc-100 text-zinc-600 border border-zinc-200',
  LOW:      'bg-[#19535f]/10 text-[#19535f] border border-[#19535f]/20',
  NONE:     'bg-zinc-50 text-zinc-400 border border-zinc-100',
}

// ── Shared helpers ─────────────────────────────────────────────────────────────
function shortId(id: string)   { return id.slice(0, 8) + '…' }
function shortUser(id: string) { return '…' + id.slice(-4) }

function Th({ children }: { children: ReactNode }) {
  return (
    <th className="text-left px-3 py-2 text-[10px] font-bold text-zinc-500 uppercase tracking-wide whitespace-nowrap">
      {children}
    </th>
  )
}

function VerdictBanner({ verdict, children }: { verdict: string; children: ReactNode }) {
  const s = BANNER_STYLE[verdict] ?? BANNER_STYLE.CLEAR
  return (
    <div className={`flex flex-wrap items-center gap-2 px-4 py-3 rounded-md border ${s.banner}`}>
      <span className={`text-sm font-bold font-mono tracking-widest ${s.label}`}>{verdict}</span>
      <span className="text-zinc-300">·</span>
      {children}
    </div>
  )
}

// ── Duplicate result panel ─────────────────────────────────────────────────────
function DuplicateResultPanel({ data }: { data: DuplicateFindings }) {
  const userPairCount: Record<string, number> = {}
  for (const f of data.pairs) userPairCount[f.user_id] = (userPairCount[f.user_id] ?? 0) + 1
  const repeats = Object.entries(userPairCount).filter(([, n]) => n > 1)

  return (
    <div className="space-y-3">
      <VerdictBanner verdict={data.verdict}>
        <span className="text-xs text-zinc-500 font-mono">{data.total_orders} orders scanned</span>
        {data.pairs.length > 0 && (
          <>
            <span className="text-zinc-300">·</span>
            <span className="text-xs text-zinc-500 font-mono">{data.pairs.length} pairs</span>
            <span className="text-zinc-300">·</span>
            <span className="text-xs text-zinc-500 font-mono">
              {data.pairs.filter(f => f.suspicion === 'HIGH').length} HIGH
              {data.pairs.filter(f => f.suspicion === 'MEDIUM').length > 0 && `, ${data.pairs.filter(f => f.suspicion === 'MEDIUM').length} MEDIUM`}
              {data.pairs.filter(f => f.suspicion === 'LOW').length > 0 && `, ${data.pairs.filter(f => f.suspicion === 'LOW').length} LOW`}
            </span>
          </>
        )}
      </VerdictBanner>

      {data.pairs.length > 0 ? (
        <div className="rounded-md border border-zinc-200 overflow-hidden">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="bg-zinc-50 border-b border-zinc-200">
                {['Suspicion', 'Ticker', 'Side', 'Qty', 'Price', 'Gap', 'User', 'Original', 'Duplicate'].map(h => <Th key={h}>{h}</Th>)}
              </tr>
            </thead>
            <tbody>
              {data.pairs.map((f, i) => (
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

// ── Escalation result panel ────────────────────────────────────────────────────
function EscalationResultPanel({ data }: { data: EscalationFindings }) {
  const [expanded, setExpanded] = useState<number | null>(null)

  return (
    <div className="space-y-3">
      <VerdictBanner verdict={data.overall_verdict}>
        <span className="text-xs text-zinc-500 font-mono">
          {data.total_investigated} order{data.total_investigated !== 1 ? 's' : ''} investigated
        </span>
        <span className="text-zinc-300">·</span>
        <span className="text-xs text-zinc-400 font-mono">{data.mode}</span>
      </VerdictBanner>

      <div className="rounded-md border border-zinc-200 overflow-hidden">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-zinc-50 border-b border-zinc-200">
              {['Verdict', 'Priority', 'Ticker', 'Failure Reason', 'Retry', 'Incident', ''].map(h => <Th key={h}>{h}</Th>)}
            </tr>
          </thead>
          <tbody>
            {data.verdicts.map((row, i) => (
              <Fragment key={i}>
                <tr
                  onClick={() => setExpanded(expanded === i ? null : i)}
                  className={`border-b border-zinc-100 cursor-pointer hover:bg-zinc-50 transition-colors ${expanded === i ? 'bg-zinc-50' : i % 2 === 1 ? 'bg-zinc-50/40' : 'bg-white'}`}
                >
                  <td className="px-3 py-2">
                    <span className={`inline-block px-1.5 py-0.5 rounded-sm text-[9px] font-bold font-mono tracking-widest ${ESC_VERDICT_BADGE[row.verdict]}`}>
                      {row.verdict}
                    </span>
                  </td>
                  <td className="px-3 py-2">
                    <span className={`inline-block px-1.5 py-0.5 rounded-sm text-[9px] font-semibold font-mono tracking-wide ${ESC_PRIORITY_BADGE[row.priority]}`}>
                      {row.priority}
                    </span>
                  </td>
                  <td className="px-3 py-2 font-mono font-semibold text-zinc-800">{row.ticker}</td>
                  <td className="px-3 py-2 font-mono text-zinc-500 text-[10px] whitespace-nowrap">{row.failure_reason}</td>
                  <td className="px-3 py-2 font-mono text-zinc-500 text-center">{row.retry_count}</td>
                  <td className="px-3 py-2 font-mono text-[10px]">
                    {row.incident_id
                      ? <span className="text-[#7b2d26] font-semibold">{row.incident_id}</span>
                      : <span className="text-zinc-300">—</span>}
                  </td>
                  <td className="px-3 py-2 text-[10px] text-zinc-300">{expanded === i ? '▲' : '▸'}</td>
                </tr>

                {expanded === i && (
                  <tr className="border-b border-zinc-100 last:border-0">
                    <td colSpan={7} className="px-4 py-3 bg-zinc-50/80">
                      <div className="grid grid-cols-2 gap-4">
                        {row.evidence.length > 0 && (
                          <div>
                            <p className="text-[10px] font-bold text-zinc-400 uppercase tracking-wide mb-1.5">Evidence</p>
                            <ul className="space-y-1">
                              {row.evidence.map((e, j) => (
                                <li key={j} className="text-[11px] text-zinc-600 leading-snug">· {e}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                        {row.recommended_actions.length > 0 && (
                          <div>
                            <p className="text-[10px] font-bold text-zinc-400 uppercase tracking-wide mb-1.5">Actions Taken</p>
                            <ul className="space-y-1">
                              {row.recommended_actions.map((a, j) => (
                                <li key={j} className="text-[11px] text-zinc-600 leading-snug">· {a}</li>
                              ))}
                            </ul>
                          </div>
                        )}
                        <div>
                          <p className="text-[10px] font-bold text-zinc-400 uppercase tracking-wide mb-1.5">Details</p>
                          <div className="space-y-0.5 text-[11px] font-mono text-zinc-500">
                            <p>saga: {row.saga_status} · step: {row.failed_step}</p>
                            <p>confidence: {(row.confidence * 100).toFixed(0)}%{row.systemic_risk ? ' · systemic risk' : ''}</p>
                            <p title={row.order_id}>order: {shortId(row.order_id)}</p>
                          </div>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}

// ── Compliance result panel ────────────────────────────────────────────────────
function ComplianceResultPanel({ data }: { data: ComplianceFindings }) {
  const breakdown = Object.entries(data.violation_breakdown ?? {}).filter(([, n]) => n > 0)

  return (
    <div className="space-y-3">
      <VerdictBanner verdict={data.verdict}>
        <span className="text-xs text-zinc-500 font-mono">
          {data.total_breaches} breach{data.total_breaches !== 1 ? 'es' : ''}
        </span>
        {breakdown.map(([type, count]) => (
          <Fragment key={type}>
            <span className="text-zinc-300">·</span>
            <span className="text-xs text-zinc-400 font-mono">{count} {type.replace('_', ' ').toLowerCase()}</span>
          </Fragment>
        ))}
        {data.period && (
          <>
            <span className="text-zinc-300">·</span>
            <span className="text-xs text-zinc-400 font-mono">{data.period.from} → {data.period.to}</span>
          </>
        )}
      </VerdictBanner>

      {data.repeat_offenders?.length > 0 ? (
        <div className="rounded-md border border-zinc-200 overflow-hidden">
          <table className="w-full text-xs border-collapse">
            <thead>
              <tr className="bg-zinc-50 border-b border-zinc-200">
                {['User', 'Breaches', 'Latest', 'Types'].map(h => <Th key={h}>{h}</Th>)}
              </tr>
            </thead>
            <tbody>
              {data.repeat_offenders.map((o, i) => (
                <tr key={i} className={`border-b border-zinc-100 last:border-0 ${i % 2 === 1 ? 'bg-zinc-50/60' : 'bg-white'}`}>
                  <td className="px-3 py-2 font-mono text-zinc-500 text-[10px]" title={o.user_id}>{shortUser(o.user_id)}</td>
                  <td className="px-3 py-2 font-mono font-semibold text-zinc-800">{o.breach_count}</td>
                  <td className="px-3 py-2 font-mono text-zinc-500 text-[10px]">{o.latest_breach}</td>
                  <td className="px-3 py-2">
                    <div className="flex gap-1 flex-wrap">
                      {o.types.map(t => (
                        <span key={t} className="inline-block px-1.5 py-0.5 rounded-sm text-[9px] font-bold font-mono bg-[#c47d0e]/15 text-[#c47d0e] border border-[#c47d0e]/20">
                          {t.replace('_VIOLATION', '').replace('_', ' ')}
                        </span>
                      ))}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="rounded-md border border-zinc-200 bg-white px-4 py-3">
          <p className="text-sm text-zinc-500 font-mono">No repeat offenders.</p>
        </div>
      )}
    </div>
  )
}

// ── Triage result panel ────────────────────────────────────────────────────────
function TriageResultPanel({ data }: { data: TriageFindings }) {
  const rows: [string, string][] = [
    ['Order ID',       data.order_id],
    ['Ticker',         data.ticker],
    ['Order Status',   data.status],
    ['User',           data.user_id],
    ['Saga Status',    data.saga_status],
    ['Failed Step',    data.failed_step],
    ['Failure Reason', data.failure_reason],
    ['Retries',        String(data.retry_count)],
  ]

  return (
    <div className="space-y-3">
      <VerdictBanner verdict={data.recommendation}>
        <span className="text-xs text-zinc-500 font-mono">{data.ticker}</span>
        <span className="text-zinc-300">·</span>
        <span className="text-xs text-zinc-400 font-mono">{data.status}</span>
      </VerdictBanner>

      <div className="rounded-md border border-zinc-200 overflow-hidden">
        <table className="w-full text-xs border-collapse">
          <tbody>
            {rows.map(([field, value], i) => (
              <tr key={field} className={`border-b border-zinc-100 last:border-0 ${i % 2 === 1 ? 'bg-zinc-50/60' : 'bg-white'}`}>
                <td className="px-3 py-2 text-[10px] font-bold text-zinc-400 uppercase tracking-wide whitespace-nowrap w-36">
                  {field}
                </td>
                <td className="px-3 py-2 font-mono text-[11px] text-zinc-700 break-all">{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {data.reasoning && (
        <div className="rounded-md border border-zinc-200 bg-white px-4 py-3">
          <p className="text-[10px] font-bold text-zinc-400 uppercase tracking-wide mb-1.5">Reasoning</p>
          <p className="text-[11px] text-zinc-600 leading-relaxed">{data.reasoning}</p>
        </div>
      )}
    </div>
  )
}

// ── ResultPanel — detects schema and dispatches ────────────────────────────────
function ResultPanel({ answer }: { answer: string }) {
  const jsonMatch = answer.match(/<findings_json>([\s\S]*?)<\/findings_json>/)
  let raw: Record<string, unknown> | null = null
  try {
    if (jsonMatch) raw = JSON.parse(jsonMatch[1].trim())
  } catch {}

  if (raw) {
    if ('pairs' in raw)
      return <DuplicateResultPanel data={raw as unknown as DuplicateFindings} />
    if ('verdicts' in raw)
      return <EscalationResultPanel data={raw as unknown as EscalationFindings} />
    if ('violation_breakdown' in raw || 'repeat_offenders' in raw)
      return <ComplianceResultPanel data={raw as unknown as ComplianceFindings} />
    if ('failed_step' in raw)
      return <TriageResultPanel data={raw as unknown as TriageFindings} />
  }

  const narrative = answer.replace(/<findings_json>[\s\S]*?<\/findings_json>/, '').trim()
  return <NarrativeText text={narrative} />
}

// ── Narrative fallback ─────────────────────────────────────────────────────────
function NarrativeText({ text }: { text: string }) {
  const lines = text.split('\n')
  return (
    <div className="space-y-1 text-sm leading-relaxed">
      {lines.map((line, i) => {
        if (/^# /.test(line))   return <p key={i} className="text-base font-bold text-zinc-900 mt-2">{line.slice(2)}</p>
        if (/^## /.test(line))  return <p key={i} className="text-sm font-bold text-zinc-800 mt-2">{line.slice(3)}</p>
        if (/^### /.test(line)) return <p key={i} className="text-xs font-bold text-zinc-700 uppercase tracking-wide mt-2">{line.slice(4)}</p>
        if (line.trim() === '') return <div key={i} className="h-1" />
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

// ── Timeline internals ─────────────────────────────────────────────────────────
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

// ── Timeline ───────────────────────────────────────────────────────────────────
export function Timeline({ events }: { events: AgentEvent[] }) {
  const { iterations, terminal } = groupEvents(events)
  if (iterations.length === 0 && !terminal) return null

  const lineFollows = (gi: number) => gi < iterations.length - 1 || terminal !== null

  return (
    <div>
      {iterations.map((group, gi) => (
        <div key={group.iteration} className="flex">
          <div className="flex flex-col items-center w-6 shrink-0 mr-4">
            <div className="w-5 h-5 rounded-full border border-[#0b7a75]/40 bg-white flex items-center justify-center shrink-0 z-10">
              <span className="text-[9px] font-semibold text-[#19535f] leading-none font-mono">{group.iteration}</span>
            </div>
            {lineFollows(gi) && <div className="w-px flex-1 bg-[#d7c9aa] my-1" />}
          </div>
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
