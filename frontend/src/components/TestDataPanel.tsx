import { useState } from 'react'

// ── Types ─────────────────────────────────────────────────────────────────────
type SeedStatus = 'idle' | 'running' | 'done' | 'error'

interface SeedLevel {
  label: string
  level: string
  desc: string
  color: string
}

interface RefRow {
  label: string
  id: string
  note: string
}

interface AgentCard {
  id: string
  label: string
  ticket: string
  description: string
  seedLevels: SeedLevel[]
  refs: RefRow[]
  footnote: string | null
}

// ── Static data ───────────────────────────────────────────────────────────────
const USERS: RefRow[] = [
  { label: 'trader1',       id: 'a1000000-0000-0000-0000-000000000001', note: 'password123' },
  { label: 'trader2',       id: 'a1000000-0000-0000-0000-000000000004', note: 'password123' },
  { label: 'bot-operator1', id: 'a1000000-0000-0000-0000-000000000003', note: 'password123' },
]

const AGENT_CARDS: AgentCard[] = [
  {
    id: 'duplicate',
    label: 'Duplicate Detection',
    ticket: 'EQ-136',
    description: 'Same-user identical orders within a short window — classified HIGH/MED/LOW by time gap.',
    seedLevels: [
      { label: 'HIGH', level: 'HIGH', desc: '~0.2s gap', color: 'bg-[#7b2d26] border-[#7b2d26] hover:bg-[#5e2219] hover:border-[#5e2219]' },
      { label: 'MED',  level: 'MED',  desc: '~2s gap',   color: 'bg-[#c47d0e] border-[#c47d0e] hover:bg-[#9e6409] hover:border-[#9e6409]' },
      { label: 'LOW',  level: 'LOW',  desc: '~7s gap',   color: 'bg-[#19535f] border-[#19535f] hover:bg-[#0f3840] hover:border-[#0f3840]' },
    ],
    refs: [],
    footnote: 'After seeding, ask: "Find duplicate orders today"',
  },
  {
    id: 'escalation',
    label: 'Failure Escalation',
    ticket: 'EQ-132',
    description: 'Diagnoses FAILED orders — RETRY, NO_ACTION, INVESTIGATE, ESCALATE, or FLAG_SYSTEMIC.',
    seedLevels: [
      { label: 'STANDARD', level: 'standard', desc: 'One of each failure type', color: 'bg-[#19535f] border-[#19535f] hover:bg-[#0f3840] hover:border-[#0f3840]' },
      { label: 'SYSTEMIC', level: 'systemic', desc: '3 NETWORK_ERROR orders',   color: 'bg-[#7b2d26] border-[#7b2d26] hover:bg-[#5e2219] hover:border-[#5e2219]' },
    ],
    refs: [
      { label: 'FAILED · NETWORK_ERROR',   id: 'c1000000-0000-0000-0000-000000000001', note: 'AAPL → RETRY' },
      { label: 'FAILED · COMPLIANCE_REJ',  id: 'c1000000-0000-0000-0000-000000000002', note: 'TSLA → NO_ACTION' },
      { label: 'FAILED · INSUF_FUNDS',     id: 'c1000000-0000-0000-0000-000000000003', note: 'NVDA → INVESTIGATE' },
      { label: 'FAILED · COMPENSATING ★', id: 'c1000000-0000-0000-0000-000000000004', note: 'MSFT → ESCALATE CRITICAL' },
    ],
    footnote: '★ COMPENSATING always ESCALATE CRITICAL · ⏱ SYSTEMIC seed expires after 15 min',
  },
  {
    id: 'compliance',
    label: 'Compliance Monitor',
    ticket: 'EQ-136',
    description: 'Summarizes wash-sale and insufficient-funds violations and flags repeat-breach accounts.',
    seedLevels: [],
    refs: [
      { label: 'REJECTED · INSUF_FUNDS',  id: 'b1000000-0000-0000-0000-000000000010', note: 'trader1 · AAPL 9999 qty' },
      { label: 'REJECTED · WASH_SALE ★',  id: 'b1000000-0000-0000-0000-000000000011', note: 'trader2 · TSLA — cross-user' },
      { label: 'REJECTED · INSUF_FUNDS',  id: 'b1000000-0000-0000-0000-000000000012', note: 'trader1 · NVDA 5000 qty' },
      { label: 'REJECTED · WASH_SALE',    id: 'b1000000-0000-0000-0000-000000000016', note: 'trader1 · TSLA' },
      { label: 'REJECTED · WASH_SALE',    id: 'b1000000-0000-0000-0000-000000000017', note: 'trader2 · AAPL' },
    ],
    footnote: '★ EQ-140: bot-operator1 reading trader2\'s order',
  },
  {
    id: 'triage',
    label: 'Order Triage',
    ticket: 'EQ-136',
    description: 'Traces the saga and audit trail of a specific order to pinpoint root cause.',
    seedLevels: [],
    refs: [
      { label: 'PENDING',   id: 'b1000000-0000-0000-0000-000000000001', note: 'trader1 · AAPL BUY MARKET' },
      { label: 'OPEN',      id: 'b1000000-0000-0000-0000-000000000002', note: 'trader1 · TSLA BUY LIMIT' },
      { label: 'FILLED',    id: 'b1000000-0000-0000-0000-000000000004', note: 'trader1 · MSFT BUY' },
      { label: 'CANCELLED', id: 'b1000000-0000-0000-0000-000000000005', note: 'trader1 · GOOG SELL' },
    ],
    footnote: null,
  },
]

// ── CopyRow ───────────────────────────────────────────────────────────────────
function CopyRow({ label, id, note }: RefRow) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(id)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <tr className="hover:bg-zinc-50 cursor-pointer group" onClick={copy} title={`Click to copy: ${id}`}>
      <td className="py-0.5 pr-2 text-[10px] text-zinc-600 whitespace-nowrap font-medium">{label}</td>
      <td className="py-0.5 pr-2 font-mono text-[9px] text-zinc-400 whitespace-nowrap">{id}</td>
      <td className="py-0.5 pr-1 text-[9px] text-zinc-400 whitespace-nowrap">{note}</td>
      <td className="py-0.5 w-8 text-right text-[9px]">
        {copied
          ? <span className="text-[#0b7a75]">✓</span>
          : <span className="text-zinc-300 group-hover:text-zinc-500">copy</span>}
      </td>
    </tr>
  )
}

// ── TestDataPanel ─────────────────────────────────────────────────────────────
export function TestDataPanel() {
  const [open, setOpen] = useState(false)
  const [seedStatus, setSeedStatus] = useState<Record<string, SeedStatus>>({})
  const [seedLogs, setSeedLogs] = useState<Record<string, string[]>>({})

  async function seedAgent(agentId: string, level: string) {
    setSeedStatus(prev => ({ ...prev, [agentId]: 'running' }))
    setSeedLogs(prev => ({ ...prev, [agentId]: [] }))

    const res = await fetch('/api/seed', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent: agentId, level }),
    })

    if (!res.ok || !res.body) {
      setSeedStatus(prev => ({ ...prev, [agentId]: 'error' }))
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() ?? ''

      for (const line of lines) {
        if (!line.startsWith('data:')) continue
        const raw = line.slice(5).trim()
        if (!raw) continue
        try {
          const event = JSON.parse(raw)
          if (event.type === 'log') {
            setSeedLogs(prev => ({ ...prev, [agentId]: [...(prev[agentId] ?? []), event.line] }))
          } else if (event.type === 'done') {
            setSeedStatus(prev => ({ ...prev, [agentId]: 'done' }))
          } else if (event.type === 'error') {
            setSeedLogs(prev => ({ ...prev, [agentId]: [...(prev[agentId] ?? []), `ERROR: ${event.message}`] }))
            setSeedStatus(prev => ({ ...prev, [agentId]: 'error' }))
          }
        } catch {
          // skip malformed SSE lines
        }
      }
    }
  }

  return (
    <div className="fixed bottom-4 right-4 z-50 text-left">
      {open && (
        <div className="mb-2 rounded-lg border border-zinc-200 bg-white shadow-xl w-[440px] max-h-[80vh] overflow-y-auto">

          {/* Panel header */}
          <div className="px-4 py-2.5 border-b border-zinc-100 sticky top-0 bg-white z-10 flex items-center justify-between">
            <span className="text-xs font-semibold text-zinc-700 tracking-wide">Test Data</span>
            <span className="text-[10px] text-zinc-300">click UUIDs to copy</span>
          </div>

          {/* Shared users */}
          <div className="px-4 py-3 border-b border-zinc-100">
            <p className="text-[10px] text-zinc-300 font-semibold uppercase tracking-wide mb-1.5">Users</p>
            <table className="w-full">
              <tbody>
                {USERS.map(u => <CopyRow key={u.id} {...u} />)}
              </tbody>
            </table>
          </div>

          {/* Per-agent cards */}
          {AGENT_CARDS.map((card, i) => {
            const status = seedStatus[card.id]
            const logs   = seedLogs[card.id] ?? []

            return (
              <div key={card.id} className={`px-4 py-3 ${i < AGENT_CARDS.length - 1 ? 'border-b border-zinc-100' : ''}`}>

                {/* Card header */}
                <div className="flex items-center gap-2 mb-1">
                  <span className="text-sm font-bold text-zinc-800">{card.label}</span>
                  <span className="text-[10px] font-mono text-[#0b7a75]">{card.ticket}</span>
                </div>
                <p className="text-[11px] text-zinc-400 leading-snug line-clamp-2 mb-2.5">{card.description}</p>

                {/* Seed buttons */}
                {card.seedLevels.length > 0 && (
                  <div className="mb-2.5">
                    <p className="text-[10px] text-zinc-400 font-semibold uppercase tracking-wide mb-1.5">Seed</p>
                    <div className="flex gap-1.5">
                      {card.seedLevels.map(preset => (
                        <button
                          key={preset.label}
                          onClick={() => seedAgent(card.id, preset.level)}
                          disabled={status === 'running'}
                          title={preset.desc}
                          className={`flex-1 rounded border text-white disabled:opacity-30 disabled:cursor-not-allowed py-1.5 text-[11px] font-mono font-bold tracking-wide transition-all ${preset.color}`}
                        >
                          {status === 'running' ? '…' : preset.label}
                        </button>
                      ))}
                    </div>

                    {/* Inline seed log */}
                    {status && status !== 'idle' && (
                      <div className="mt-1.5 bg-zinc-50 border border-zinc-100 rounded px-2 py-1.5 max-h-[72px] overflow-y-auto">
                        {logs.slice(-6).map((line, j) => (
                          <p key={j} className="text-[10px] font-mono text-zinc-500 leading-relaxed whitespace-pre truncate">{line}</p>
                        ))}
                        {status === 'done'  && <p className="text-[10px] font-mono text-[#0b7a75] mt-0.5">✓ done</p>}
                        {status === 'error' && <p className="text-[10px] font-mono text-[#7b2d26] mt-0.5">✕ failed</p>}
                      </div>
                    )}
                  </div>
                )}

                {/* Reference IDs */}
                {card.refs.length > 0 && (
                  <>
                    <p className="text-[10px] text-zinc-300 font-semibold uppercase tracking-wide mb-1">Reference IDs</p>
                    <table className="w-full">
                      <tbody>
                        {card.refs.map(r => <CopyRow key={r.id} {...r} />)}
                      </tbody>
                    </table>
                  </>
                )}

                {card.footnote && (
                  <p className="mt-1.5 text-[9px] text-zinc-400">{card.footnote}</p>
                )}
              </div>
            )
          })}
        </div>
      )}

      <button
        onClick={() => setOpen(o => !o)}
        className="ml-auto flex items-center gap-1.5 rounded-md border border-zinc-200 bg-white px-3 py-1.5 text-[11px] text-zinc-600 hover:border-zinc-300 hover:text-zinc-900 transition-colors shadow-sm"
      >
        <span>Test Data</span>
        <span className="text-zinc-300">{open ? '▲' : '▼'}</span>
      </button>
    </div>
  )
}
