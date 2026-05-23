import { useState, useRef } from 'react'
import type { AgentEvent } from '../types'
import { Timeline } from './Timeline'

interface AgentConfig {
  id: string
  label: string
  description: string
  ticket: string
  placeholder: string
  examples: string[]
  ready: boolean
}

const AGENTS: AgentConfig[] = [
  {
    id: 'duplicate',
    label: 'Duplicate Detection',
    description: 'Scans orders for identical submissions by the same user within a time window and assigns HIGH / MEDIUM / LOW suspicion.',
    ticket: 'EQ-136',
    placeholder: 'Scan today\'s orders for duplicates',
    examples: [
      'Scan today\'s orders for duplicates',
      'Check for duplicate orders this week',
    ],
    ready: true,
  },
  {
    id: 'compliance',
    label: 'Compliance Monitor',
    description: 'Summarises all compliance breaches for a given period — breach count, violation type breakdown, and repeat offenders.',
    ticket: 'EQ-130',
    placeholder: 'Show all compliance breaches today',
    examples: [
      'Show all compliance breaches today',
      'Which accounts have repeated wash-sale violations this week?',
    ],
    ready: true,
  },
  {
    id: 'triage',
    label: 'Order Triage',
    description: 'Given a stuck or failed order UUID, traces the saga, audit log, and retry history to identify root cause and recommend action.',
    ticket: 'EQ-130',
    placeholder: 'Triage order <paste UUID here>',
    examples: [
      'Triage order <paste a FAILED order UUID>',
      'Why is order <UUID> stuck?',
    ],
    ready: true,
  },
  {
    id: 'escalation',
    label: 'Failure Escalation',
    description: 'Triggered when an order reaches FAILED or COMPENSATION_REQUIRED — diagnoses root cause and auto-creates a PagerDuty incident if needed.',
    ticket: 'EQ-132',
    placeholder: '',
    examples: [],
    ready: false,
  },
  {
    id: 'settlement',
    label: 'Settlement Reconciliation',
    description: 'EOD scan of filled orders against settlement records — flags stuck settlements, classifies by retry count, and recommends action.',
    ticket: 'EQ-134',
    placeholder: '',
    examples: [],
    ready: false,
  },
]

type RunStatus = 'idle' | 'running' | 'done' | 'error'

export function AgentRunner() {
  const [selectedId, setSelectedId] = useState<string>(AGENTS.filter(a => a.ready)[0].id)
  const [question, setQuestion] = useState('')
  const [events, setEvents] = useState<AgentEvent[]>([])
  const [status, setStatus] = useState<RunStatus>('idle')
  const bottomRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<(() => void) | null>(null)

  const selectedAgent = AGENTS.find(a => a.id === selectedId)!

  function scrollToBottom() {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  function selectAgent(id: string) {
    if (status === 'running') return
    setSelectedId(id)
    setQuestion('')
    setEvents([])
    setStatus('idle')
  }

  async function run() {
    if (!question.trim() || !selectedAgent.ready) return
    setEvents([])
    setStatus('running')

    const res = await fetch('/api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent: selectedId, question }),
    })

    if (!res.ok || !res.body) {
      setStatus('error')
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    abortRef.current = () => reader.cancel()

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
          const event: AgentEvent = JSON.parse(raw)
          setEvents(prev => [...prev, event])
          setTimeout(scrollToBottom, 50)
          if (event.type === 'done' || event.type === 'error') {
            setStatus(event.type === 'done' ? 'done' : 'error')
          }
        } catch {
          // skip malformed lines
        }
      }
    }
  }

  function stop() {
    abortRef.current?.()
    setStatus('idle')
  }

  function reset() {
    stop()
    setEvents([])
    setStatus('idle')
  }

  return (
    <div className="flex h-screen max-w-6xl mx-auto p-6 gap-6">

      {/* Sidebar — agent picker */}
      <div className="w-64 shrink-0 flex flex-col gap-2">
        <div className="mb-2">
          <h1 className="text-lg font-bold text-white tracking-tight">EquiFlow Agents</h1>
          <p className="text-slate-500 text-xs mt-0.5">Select an agent to run</p>
        </div>

        {AGENTS.map(agent => (
          <button
            key={agent.id}
            onClick={() => agent.ready && selectAgent(agent.id)}
            disabled={!agent.ready || status === 'running'}
            className={[
              'w-full text-left rounded-lg border px-3 py-2.5 transition-all',
              agent.ready
                ? selectedId === agent.id
                  ? 'border-violet-500 bg-violet-500/10 text-white'
                  : 'border-slate-700 bg-slate-800/50 text-slate-300 hover:border-slate-500 hover:text-white cursor-pointer'
                : 'border-slate-800 bg-slate-900/30 text-slate-600 cursor-not-allowed',
            ].join(' ')}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-sm font-medium">{agent.label}</span>
              {agent.ready
                ? <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">READY</span>
                : <span className="text-[10px] font-semibold px-1.5 py-0.5 rounded bg-slate-700/50 text-slate-500 border border-slate-700">SOON</span>
              }
            </div>
            <p className="text-[11px] mt-1 leading-relaxed text-slate-500">{agent.description}</p>
            <p className="text-[10px] mt-1.5 text-slate-600">{agent.ticket}</p>
          </button>
        ))}
      </div>

      {/* Main panel */}
      <div className="flex-1 flex flex-col gap-4 min-w-0">

        {/* Agent header */}
        <div className="border-b border-slate-800 pb-4">
          <div className="flex items-center gap-3">
            <h2 className="text-xl font-bold text-white">{selectedAgent.label}</h2>
            <span className="text-xs px-2 py-0.5 rounded-full bg-emerald-500/15 text-emerald-400 border border-emerald-500/20">
              {selectedAgent.ticket} · Ready
            </span>
          </div>
          <p className="text-slate-400 text-sm mt-1">{selectedAgent.description}</p>

          {selectedAgent.examples.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-3">
              <span className="text-xs text-slate-600">Try:</span>
              {selectedAgent.examples.map(ex => (
                <button
                  key={ex}
                  onClick={() => { if (status !== 'running') setQuestion(ex) }}
                  disabled={status === 'running'}
                  className="text-xs px-2 py-1 rounded border border-slate-700 text-slate-400 hover:border-slate-500 hover:text-slate-200 transition-colors disabled:opacity-40"
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Input row */}
        <div className="flex gap-3">
          <input
            type="text"
            value={question}
            onChange={e => setQuestion(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && status !== 'running' && run()}
            placeholder="Enter prompt here..."
            disabled={status === 'running'}
            className="flex-1 rounded-lg bg-slate-800 border border-slate-700 text-slate-200 placeholder-slate-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-50"
          />
          {status === 'running' ? (
            <button onClick={stop} className="rounded-lg bg-red-600 hover:bg-red-500 text-white px-4 py-2 text-sm font-medium transition-colors">
              Stop
            </button>
          ) : (
            <button onClick={run} disabled={!question.trim()} className="rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-40 text-white px-4 py-2 text-sm font-medium transition-colors">
              Run
            </button>
          )}
          {events.length > 0 && status !== 'running' && (
            <button onClick={reset} className="rounded-lg border border-slate-700 hover:border-slate-500 text-slate-400 hover:text-slate-200 px-4 py-2 text-sm transition-colors">
              Clear
            </button>
          )}
        </div>

        {/* Status bar */}
        {status === 'running' && (
          <div className="flex items-center gap-2 text-sm text-violet-300">
            <span className="w-2 h-2 rounded-full bg-violet-400 animate-pulse inline-block" />
            Agent running...
          </div>
        )}
        {status === 'done' && <div className="text-sm text-emerald-400">✓ Completed — {events.filter(e => e.type === 'tool_call').length} tool calls</div>}
        {status === 'error' && <div className="text-sm text-red-400">Agent encountered an error</div>}

        {/* Timeline */}
        <div className="flex-1 overflow-y-auto flex flex-col gap-3 pb-4">
          {events.length === 0 && status === 'idle' && (
            <div className="flex-1 flex items-center justify-center text-slate-600 text-sm">
              Enter a question and press Run
            </div>
          )}
          <Timeline events={events} />
          <div ref={bottomRef} />
        </div>
      </div>
    </div>
  )
}
