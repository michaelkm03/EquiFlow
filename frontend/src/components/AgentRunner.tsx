import { useState, useRef } from 'react'
import type { AgentEvent, SeedEvent } from '../types'
import { Timeline } from './Timeline'

interface AgentConfig {
  id: string
  label: string
  description: string
  ticket: string
  placeholder: string
  examples: string[]
  ready: boolean
  hasSeed: boolean
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
    hasSeed: true,
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
    hasSeed: false,
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
    hasSeed: false,
  },
  {
    id: 'escalation',
    label: 'Failure Escalation',
    description: 'Triggered when an order reaches FAILED or COMPENSATION_REQUIRED — diagnoses root cause and auto-creates a PagerDuty incident if needed.',
    ticket: 'EQ-132',
    placeholder: '',
    examples: [],
    ready: false,
    hasSeed: false,
  },
  {
    id: 'settlement',
    label: 'Settlement Reconciliation',
    description: 'EOD scan of filled orders against settlement records — flags stuck settlements, classifies by retry count, and recommends action.',
    ticket: 'EQ-134',
    placeholder: '',
    examples: [],
    ready: false,
    hasSeed: false,
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

  type SeedStatus = 'idle' | 'running' | 'done' | 'error'
  const [seedStatus, setSeedStatus] = useState<SeedStatus>('idle')
  const [seedLog, setSeedLog] = useState<{ phase?: string; lines: string[] }[]>([])
  const [seedMessages, setSeedMessages] = useState(20)
  const [seedDelay, setSeedDelay] = useState<'1s' | '10s' | '60s'>('1s')
  const seedBottomRef = useRef<HTMLDivElement>(null)

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

  async function seed(agentId: string) {
    selectAgent(agentId)
    setSeedLog([])
    setSeedStatus('running')

    const res = await fetch('/api/seed', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent: agentId, messages: seedMessages, delay: seedDelay }),
    })

    if (!res.ok || !res.body) {
      setSeedStatus('error')
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
          const event: SeedEvent = JSON.parse(raw)
          if (event.type === 'phase') {
            setSeedLog(prev => [...prev, { phase: event.label, lines: [] }])
          } else if (event.type === 'log') {
            setSeedLog(prev => {
              if (prev.length === 0) return [{ lines: [event.line] }]
              const updated = [...prev]
              updated[updated.length - 1] = {
                ...updated[updated.length - 1],
                lines: [...updated[updated.length - 1].lines, event.line],
              }
              return updated
            })
          } else if (event.type === 'done') {
            setSeedStatus('done')
          } else if (event.type === 'error') {
            setSeedLog(prev => {
              if (prev.length === 0) return [{ lines: [event.message] }]
              const updated = [...prev]
              updated[updated.length - 1] = {
                ...updated[updated.length - 1],
                lines: [...updated[updated.length - 1].lines, `ERROR: ${event.message}`],
              }
              return updated
            })
            setSeedStatus('error')
          }
          setTimeout(() => seedBottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 50)
        } catch {
          // skip malformed
        }
      }
    }
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
          <div key={agent.id}>
            <button
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

            {agent.hasSeed && agent.ready && (
              <div className="mt-0.5 rounded-md border border-zinc-800/70 bg-zinc-950/60 px-3 py-2">
                <p className="text-[9px] font-mono text-zinc-700 uppercase tracking-widest mb-2">Seed data</p>
                <div className="flex items-center gap-2">
                  <div className="flex items-center gap-1">
                    <input
                      type="number"
                      value={seedMessages}
                      onChange={e => setSeedMessages(Math.max(1, parseInt(e.target.value) || 1))}
                      min={1}
                      max={500}
                      className="w-12 rounded-sm bg-zinc-900 border border-zinc-800 text-zinc-300 px-1.5 py-0.5 text-[11px] font-mono text-center focus:outline-none focus:border-zinc-600"
                    />
                    <span className="text-[10px] text-zinc-600 font-mono">msgs</span>
                  </div>
                  <select
                    value={seedDelay}
                    onChange={e => setSeedDelay(e.target.value as '1s' | '10s' | '60s')}
                    className="rounded-sm bg-zinc-900 border border-zinc-800 text-zinc-400 px-1.5 py-0.5 text-[10px] font-mono focus:outline-none focus:border-zinc-600"
                  >
                    <option value="1s">HIGH</option>
                    <option value="10s">MED</option>
                    <option value="60s">LOW</option>
                  </select>
                  <button
                    onClick={() => seed(agent.id)}
                    disabled={seedStatus === 'running' || status === 'running'}
                    className="ml-auto rounded-sm border border-zinc-700 hover:border-green-500/50 text-zinc-500 hover:text-green-400 disabled:opacity-30 disabled:cursor-not-allowed px-2 py-0.5 text-[10px] font-mono tracking-widest transition-colors"
                  >
                    {seedStatus === 'running' ? 'SEEDING…' : 'SEED'}
                  </button>
                </div>
              </div>
            )}
          </div>
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

        {/* Seed log */}
        {seedLog.length > 0 && (
          <div className="rounded-md bg-zinc-950 border border-zinc-800/60 p-3 max-h-52 overflow-y-auto">
            <div className="flex items-center justify-between mb-2">
              <p className="text-[10px] font-semibold font-mono tracking-widest uppercase text-zinc-600">
                {seedStatus === 'running' ? 'Seeding data…' : seedStatus === 'done' ? '✓ Seed complete' : '✕ Seed failed'}
              </p>
              {seedStatus !== 'running' && (
                <button onClick={() => { setSeedLog([]); setSeedStatus('idle') }} className="text-[10px] text-zinc-700 hover:text-zinc-500 transition-colors">
                  dismiss
                </button>
              )}
            </div>
            {seedLog.map((block, bi) => (
              <div key={bi} className="mb-2">
                {block.phase && (
                  <p className="text-[10px] text-zinc-600 font-mono uppercase tracking-widest mb-1">── {block.phase}</p>
                )}
                {block.lines.map((line, li) => (
                  <p key={li} className="text-[11px] font-mono text-zinc-500 leading-relaxed whitespace-pre">{line}</p>
                ))}
              </div>
            ))}
            <div ref={seedBottomRef} />
          </div>
        )}

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
