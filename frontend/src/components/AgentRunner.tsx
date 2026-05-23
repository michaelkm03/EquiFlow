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
    <div className="flex h-screen max-w-6xl mx-auto p-6 gap-0">

      {/* Sidebar */}
      <div className="w-60 shrink-0 flex flex-col gap-1 border-r border-zinc-800/60 pr-5 mr-6">
        <div className="mb-5">
          <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-widest mb-1">EquiFlow</p>
          <h1 className="text-sm font-semibold text-white">Agents</h1>
        </div>

        {AGENTS.map(agent => (
          <button
            key={agent.id}
            onClick={() => agent.ready && selectAgent(agent.id)}
            disabled={!agent.ready || status === 'running'}
            className={[
              'w-full text-left rounded-md border px-3 py-2.5 transition-all duration-150',
              agent.ready
                ? selectedId === agent.id
                  ? 'border-green-500/50 bg-green-500/[0.04] text-white'
                  : 'border-zinc-800 bg-zinc-900/30 text-zinc-400 hover:border-zinc-700 hover:text-zinc-200 cursor-pointer'
                : 'border-zinc-900 bg-transparent text-zinc-700 cursor-not-allowed',
            ].join(' ')}
          >
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs font-medium">{agent.label}</span>
              {agent.ready
                ? <span className="text-[9px] font-semibold px-1.5 py-0.5 rounded-sm bg-green-500/10 text-green-400 border border-green-500/20 tracking-widest font-mono">LIVE</span>
                : <span className="text-[9px] font-semibold px-1.5 py-0.5 rounded-sm text-zinc-700 border border-zinc-800/80 tracking-widest font-mono">SOON</span>
              }
            </div>
            <p className="text-[11px] mt-1 leading-relaxed text-zinc-600">{agent.description}</p>
            <p className="text-[10px] mt-1.5 text-zinc-700 font-mono">{agent.ticket}</p>
          </button>
        ))}
      </div>

      {/* Main panel */}
      <div className="flex-1 flex flex-col gap-4 min-w-0">

        {/* Agent header */}
        <div className="border-b border-zinc-800/60 pb-4">
          <div className="flex items-center gap-3">
            <h2 className="text-sm font-semibold text-white tracking-tight">{selectedAgent.label}</h2>
            <span className="text-[9px] px-1.5 py-0.5 rounded-sm bg-green-500/[0.07] text-green-400/70 border border-green-500/15 font-mono tracking-widest">
              {selectedAgent.ticket}
            </span>
          </div>
          <p className="text-zinc-500 text-xs mt-1 leading-relaxed">{selectedAgent.description}</p>

          {selectedAgent.examples.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-3">
              <span className="text-[11px] text-zinc-700 self-center">Try:</span>
              {selectedAgent.examples.map(ex => (
                <button
                  key={ex}
                  onClick={() => { if (status !== 'running') setQuestion(ex) }}
                  disabled={status === 'running'}
                  className="text-[11px] px-2 py-1 rounded-sm border border-zinc-800 text-zinc-600 hover:border-zinc-600 hover:text-zinc-300 transition-colors disabled:opacity-30"
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Input row */}
        <div className="flex gap-2">
          <input
            type="text"
            value={question}
            onChange={e => setQuestion(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && status !== 'running' && run()}
            placeholder="Enter prompt here..."
            disabled={status === 'running'}
            className="flex-1 rounded-md bg-zinc-900 border border-zinc-800 text-zinc-100 placeholder-zinc-700 px-3 py-2 text-sm focus:outline-none focus:border-green-500/40 focus:ring-1 focus:ring-green-500/20 disabled:opacity-40 transition-colors"
          />
          {status === 'running' ? (
            <button onClick={stop} className="rounded-md bg-red-500/70 hover:bg-red-500/90 text-white px-4 py-2 text-[11px] font-semibold tracking-widest transition-colors font-mono">
              STOP
            </button>
          ) : (
            <button onClick={run} disabled={!question.trim()} className="rounded-md bg-green-500 hover:bg-green-400 disabled:opacity-25 text-black px-5 py-2 text-[11px] font-semibold tracking-widest transition-colors font-mono">
              RUN
            </button>
          )}
          {events.length > 0 && status !== 'running' && (
            <button onClick={reset} className="rounded-md border border-zinc-800 hover:border-zinc-700 text-zinc-600 hover:text-zinc-400 px-4 py-2 text-xs transition-colors">
              Clear
            </button>
          )}
        </div>

        {/* Status bar */}
        {status === 'running' && (
          <div className="flex items-center gap-2 text-xs text-green-400">
            <span className="w-1.5 h-1.5 rounded-full bg-green-400 animate-pulse inline-block" />
            Running
          </div>
        )}
        {status === 'done' && (
          <div className="text-[11px] text-zinc-600 font-mono">
            ✓ {events.filter(e => e.type === 'tool_call').length} tool calls
          </div>
        )}
        {status === 'error' && <div className="text-xs text-red-400">Agent encountered an error</div>}

        {/* Timeline */}
        <div className="flex-1 overflow-y-auto flex flex-col pb-4">
          {events.length === 0 && status === 'idle' && (
            <div className="flex-1 flex items-center justify-center text-zinc-700 text-sm">
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
