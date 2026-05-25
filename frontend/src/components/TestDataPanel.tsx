import { useState } from 'react'

const USERS = [
  { label: 'trader1',       id: 'a1000000-0000-0000-0000-000000000001', note: 'password123' },
  { label: 'trader2',       id: 'a1000000-0000-0000-0000-000000000004', note: 'password123' },
  { label: 'bot-operator1', id: 'a1000000-0000-0000-0000-000000000003', note: 'password123' },
]

const ORDERS = [
  { label: 'PENDING',                    id: 'b1000000-0000-0000-0000-000000000001', note: 'trader1 · AAPL BUY MARKET' },
  { label: 'OPEN',                       id: 'b1000000-0000-0000-0000-000000000002', note: 'trader1 · TSLA BUY LIMIT' },
  { label: 'FILLED',                     id: 'b1000000-0000-0000-0000-000000000004', note: 'trader1 · MSFT BUY' },
  { label: 'CANCELLED',                  id: 'b1000000-0000-0000-0000-000000000005', note: 'trader1 · GOOG SELL' },
  { label: 'REJECTED · INSUF_FUNDS',     id: 'b1000000-0000-0000-0000-000000000010', note: 'trader1 · AAPL 9999 qty' },
  { label: 'REJECTED · WASH_SALE ★',    id: 'b1000000-0000-0000-0000-000000000011', note: 'trader2 · TSLA — EQ-140 cross-user' },
  { label: 'REJECTED · INSUF_FUNDS',     id: 'b1000000-0000-0000-0000-000000000012', note: 'trader1 · NVDA 5000 qty' },
  { label: 'REJECTED · WASH_SALE',       id: 'b1000000-0000-0000-0000-000000000016', note: 'trader1 · TSLA' },
  { label: 'REJECTED · WASH_SALE',       id: 'b1000000-0000-0000-0000-000000000017', note: 'trader2 · AAPL' },
]

function CopyRow({ label, id, note }: { label: string; id: string; note: string }) {
  const [copied, setCopied] = useState(false)

  function copy() {
    navigator.clipboard.writeText(id)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <tr
      className="hover:bg-white/5 cursor-pointer group"
      onClick={copy}
      title={`Click to copy: ${id}`}
    >
      <td className="py-0.5 pr-2 text-[10px] text-[#8ab4c2] whitespace-nowrap font-medium">{label}</td>
      <td className="py-0.5 pr-2 font-mono text-[9px] text-[#c9d6dc] whitespace-nowrap">{id}</td>
      <td className="py-0.5 pr-1 text-[9px] text-[#5a7a85] whitespace-nowrap">{note}</td>
      <td className="py-0.5 w-10 text-right text-[9px]">
        {copied
          ? <span className="text-green-400">✓</span>
          : <span className="text-[#3a5a65] group-hover:text-[#8ab4c2]">copy</span>
        }
      </td>
    </tr>
  )
}

export function TestDataPanel() {
  const [open, setOpen] = useState(false)

  return (
    <div className="fixed bottom-4 right-4 z-50 text-left">
      {open && (
        <div className="mb-2 rounded-lg border border-[#1e3a42] bg-[#0d1e24] shadow-xl w-[520px] overflow-hidden">
          <div className="px-3 py-2 border-b border-[#1e3a42] flex items-center justify-between">
            <span className="text-[11px] font-semibold text-[#8ab4c2] tracking-wide uppercase">Test Data</span>
            <span className="text-[10px] text-[#3a5a65]">click any row to copy UUID</span>
          </div>

          <div className="px-3 py-2">
            <div className="text-[10px] text-[#3a5a65] uppercase tracking-wide mb-1">Users</div>
            <table className="w-full">
              <tbody>
                {USERS.map(u => <CopyRow key={u.id} label={u.label} id={u.id} note={u.note} />)}
              </tbody>
            </table>
          </div>

          <div className="px-3 pb-3 border-t border-[#1e3a42] pt-2">
            <div className="text-[10px] text-[#3a5a65] uppercase tracking-wide mb-1">Orders</div>
            <table className="w-full">
              <tbody>
                {ORDERS.map(o => <CopyRow key={o.id} label={o.label} id={o.id} note={o.note} />)}
              </tbody>
            </table>
            <p className="mt-2 text-[9px] text-[#3a5a65]">★ EQ-140 cross-user test: bot-operator1 reading trader2&apos;s order</p>
          </div>
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
