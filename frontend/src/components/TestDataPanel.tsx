import { useState } from 'react'

// ── Shared ────────────────────────────────────────────────────────────────────
const USERS = [
  { label: 'trader1',       id: 'a1000000-0000-0000-0000-000000000001', note: 'password123' },
  { label: 'trader2',       id: 'a1000000-0000-0000-0000-000000000004', note: 'password123' },
  { label: 'bot-operator1', id: 'a1000000-0000-0000-0000-000000000003', note: 'password123' },
]

// ── Per-agent seed data ───────────────────────────────────────────────────────
const AGENT_SECTIONS = [
  {
    agent: 'Compliance',
    seed: 'python equiflow-mcp/seed_compliance_orders.py',
    orders: [
      { label: 'REJECTED · INSUF_FUNDS',  id: 'b1000000-0000-0000-0000-000000000010', note: 'trader1 · AAPL 9999 qty' },
      { label: 'REJECTED · WASH_SALE ★',  id: 'b1000000-0000-0000-0000-000000000011', note: 'trader2 · TSLA — cross-user' },
      { label: 'REJECTED · INSUF_FUNDS',  id: 'b1000000-0000-0000-0000-000000000012', note: 'trader1 · NVDA 5000 qty' },
      { label: 'REJECTED · WASH_SALE',    id: 'b1000000-0000-0000-0000-000000000016', note: 'trader1 · TSLA' },
      { label: 'REJECTED · WASH_SALE',    id: 'b1000000-0000-0000-0000-000000000017', note: 'trader2 · AAPL' },
    ],
    footnote: '★ EQ-140: bot-operator1 reading trader2\'s order',
  },
  {
    agent: 'Triage',
    seed: null,
    orders: [
      { label: 'PENDING',    id: 'b1000000-0000-0000-0000-000000000001', note: 'trader1 · AAPL BUY MARKET' },
      { label: 'OPEN',       id: 'b1000000-0000-0000-0000-000000000002', note: 'trader1 · TSLA BUY LIMIT' },
      { label: 'FILLED',     id: 'b1000000-0000-0000-0000-000000000004', note: 'trader1 · MSFT BUY' },
      { label: 'CANCELLED',  id: 'b1000000-0000-0000-0000-000000000005', note: 'trader1 · GOOG SELL' },
    ],
    footnote: null,
  },
  {
    agent: 'Duplicate',
    seed: 'python equiflow-mcp/seed_duplicate_orders.py',
    orders: [],
    footnote: 'Scan-mode agent — no specific UUIDs needed. Run seed script then ask: "Find duplicate orders today"',
  },
  {
    agent: 'Escalation — Standard',
    seed: 'python equiflow-mcp/seed_failed_orders.py',
    orders: [
      { label: 'FAILED · NETWORK_ERROR',    id: 'c1000000-0000-0000-0000-000000000001', note: 'AAPL → RETRY' },
      { label: 'FAILED · COMPLIANCE_REJ',   id: 'c1000000-0000-0000-0000-000000000002', note: 'TSLA → NO_ACTION' },
      { label: 'FAILED · INSUF_FUNDS',      id: 'c1000000-0000-0000-0000-000000000003', note: 'NVDA → INVESTIGATE (bal recovered)' },
      { label: 'FAILED · COMPENSATING ★',  id: 'c1000000-0000-0000-0000-000000000004', note: 'MSFT → ESCALATE CRITICAL' },
    ],
    footnote: '★ Saga in COMPENSATING state — always ESCALATE CRITICAL',
  },
  {
    agent: 'Escalation — Systemic',
    seed: 'python equiflow-mcp/seed_failed_orders.py --systemic',
    orders: [
      { label: 'FAILED · NETWORK_ERROR ⏱', id: 'c1100000-0000-0000-0000-000000000001', note: 'AAPL → FLAG_SYSTEMIC' },
      { label: 'FAILED · NETWORK_ERROR ⏱', id: 'c1100000-0000-0000-0000-000000000002', note: 'GOOGL → FLAG_SYSTEMIC' },
      { label: 'FAILED · NETWORK_ERROR ⏱', id: 'c1100000-0000-0000-0000-000000000003', note: 'AMZN → FLAG_SYSTEMIC' },
    ],
    footnote: '⏱ Timestamps expire after 15 min — re-seed if needed',
  },
]

// ── Components ────────────────────────────────────────────────────────────────
function CopyRow({ label, id, note }: { label: string; id: string; note: string }) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(id)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <tr className="hover:bg-white/5 cursor-pointer group" onClick={copy} title={`Click to copy: ${id}`}>
      <td className="py-0.5 pr-2 text-[10px] text-[#8ab4c2] whitespace-nowrap font-medium">{label}</td>
      <td className="py-0.5 pr-2 font-mono text-[9px] text-[#c9d6dc] whitespace-nowrap">{id}</td>
      <td className="py-0.5 pr-1 text-[9px] text-[#5a7a85] whitespace-nowrap">{note}</td>
      <td className="py-0.5 w-10 text-right text-[9px]">
        {copied
          ? <span className="text-green-400">✓</span>
          : <span className="text-[#3a5a65] group-hover:text-[#8ab4c2]">copy</span>}
      </td>
    </tr>
  )
}

function CopySeed({ cmd }: { cmd: string }) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(cmd)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div
      className="flex items-center gap-2 mb-1.5 cursor-pointer group"
      onClick={copy}
      title="Click to copy seed command"
    >
      <code className="text-[9px] text-[#4a8a9a] font-mono bg-[#0a161b] px-2 py-0.5 rounded flex-1 truncate">
        {cmd}
      </code>
      <span className="text-[9px] shrink-0">
        {copied
          ? <span className="text-green-400">✓ copied</span>
          : <span className="text-[#3a5a65] group-hover:text-[#8ab4c2]">copy</span>}
      </span>
    </div>
  )
}

export function TestDataPanel() {
  const [open, setOpen] = useState(false)

  return (
    <div className="fixed bottom-4 right-4 z-50 text-left">
      {open && (
        <div className="mb-2 rounded-lg border border-[#1e3a42] bg-[#0d1e24] shadow-xl w-[580px] max-h-[80vh] overflow-y-auto">
          <div className="px-3 py-2 border-b border-[#1e3a42] flex items-center justify-between sticky top-0 bg-[#0d1e24] z-10">
            <span className="text-[11px] font-semibold text-[#8ab4c2] tracking-wide uppercase">Test Data</span>
            <span className="text-[10px] text-[#3a5a65]">click any row or command to copy</span>
          </div>

          {/* Users */}
          <div className="px-3 py-2 border-b border-[#1e3a42]">
            <div className="text-[10px] text-[#3a5a65] uppercase tracking-wide mb-1">Users (shared)</div>
            <table className="w-full">
              <tbody>
                {USERS.map(u => <CopyRow key={u.id} label={u.label} id={u.id} note={u.note} />)}
              </tbody>
            </table>
          </div>

          {/* Per-agent sections */}
          {AGENT_SECTIONS.map((section, i) => (
            <div key={section.agent} className={`px-3 py-2 ${i < AGENT_SECTIONS.length - 1 ? 'border-b border-[#1e3a42]' : ''}`}>
              <div className="text-[10px] text-[#3a5a65] uppercase tracking-wide mb-1.5">{section.agent}</div>

              {section.seed && <CopySeed cmd={section.seed} />}

              {section.orders.length > 0 && (
                <table className="w-full">
                  <tbody>
                    {section.orders.map(o => <CopyRow key={o.id} label={o.label} id={o.id} note={o.note} />)}
                  </tbody>
                </table>
              )}

              {section.footnote && (
                <p className="mt-1.5 text-[9px] text-[#3a5a65]">{section.footnote}</p>
              )}
            </div>
          ))}
        </div>
      )}

      <button
        onClick={() => setOpen(o => !o)}
        className="ml-auto flex items-center gap-1.5 rounded-md border border-[#1e3a42] bg-[#0d1e24] px-3 py-1.5 text-[11px] text-[#8ab4c2] hover:border-[#2a5a6a] hover:text-white transition-colors shadow-lg"
      >
        <span>🗂</span>
        <span>Test IDs</span>
        <span className="text-[#3a5a65]">{open ? '▲' : '▼'}</span>
      </button>
    </div>
  )
}
