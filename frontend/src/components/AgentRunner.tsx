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
}

const AGENTS: AgentConfig[] = [
  {
    id: 'duplicate',
    label: 'Duplicate Detection',
    description: 'Detects when the same user submits identical orders within a short window and classifies each pair as HIGH, MEDIUM, or LOW suspicion based on the time gap.',
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
    description: 'Summarizes all wash-sale and insufficient-funds violations for a given period and flags accounts with repeat breaches.',
    ticket: 'EQ-136',
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
    description: 'Traces the saga and audit trail of a specific failed or stuck order to pinpoint root cause and recommend a concrete action.',
    ticket: 'EQ-136',
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
    description: 'Diagnoses the root cause of every FAILED order and decides whether to recommend a retry, flag for investigation, or create a PagerDuty incident.',
    ticket: 'EQ-132',
    placeholder: 'Escalate any failed orders from the last hour',
    examples: [
      'Escalate any failed orders from the last hour',
      'Investigate failed orders today',
    ],
    ready: true,
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

const SEED_PRESETS = [
  { label: 'HIGH' as const, desc: '~0.2s gap',  color: 'bg-[#7b2d26] border-[#7b2d26] hover:bg-[#5e2219] hover:border-[#5e2219]' },
  { label: 'MED'  as const, desc: '~2s gap',    color: 'bg-[#c47d0e] border-[#c47d0e] hover:bg-[#9e6409] hover:border-[#9e6409]' },
  { label: 'LOW'  as const, desc: '~7s gap',    color: 'bg-[#19535f] border-[#19535f] hover:bg-[#0f3840] hover:border-[#0f3840]' },
]

type SeedLevel = typeof SEED_PRESETS[number]['label']
type RunStatus = 'idle' | 'running' | 'done' | 'error'
type SeedStatus = 'idle' | 'running' | 'done' | 'error'

export function AgentRunner() {
  type RunMode = 'live' | 'local'

  const [selectedId, setSelectedId] = useState<string>(AGENTS.filter(a => a.ready)[0].id)
  const [question, setQuestion] = useState('')
  const [events, setEvents] = useState<AgentEvent[]>([])
  const [status, setStatus] = useState<RunStatus>('idle')
  const [mode, setMode] = useState<RunMode>('live')
  const bottomRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<(() => void) | null>(null)

  const [seedStatus, setSeedStatus] = useState<SeedStatus>('idle')
  const [seedLog, setSeedLog] = useState<{ phase?: string; lines: string[] }[]>([])
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
      body: JSON.stringify({ agent: selectedId, question, mode }),
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

  async function cleanup() {
    setSeedLog([])
    setSeedStatus('running')
    const res = await fetch('/api/cleanup', { method: 'POST' })
    await streamSeedResponse(res)
  }

  async function seed(level: SeedLevel) {
    setSeedLog([])
    setSeedStatus('running')
    const res = await fetch('/api/seed', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent: 'duplicate', level }),
    })
    await streamSeedResponse(res)
  }

  async function streamSeedResponse(res: Response) {
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
    <div className="flex h-screen max-w-6xl mx-auto p-6 gap-0 bg-[#f0f3f5]">

      {/* Sidebar — flat agent list, no card chrome */}
      <div className="w-60 shrink-0 flex flex-col border-r border-zinc-200 pr-5 mr-7">
        <p className="text-xs font-semibold text-zinc-400 mb-5 tracking-wide">EquiFlow</p>

        {/* Agent list */}
        <div className="flex flex-col flex-1">
          {AGENTS.filter(a => a.ready).map((agent, i, arr) => (
            <div
              key={agent.id}
              className={[
                'transition-all duration-150 border-l-2',
                i < arr.length - 1 ? 'border-b border-zinc-100' : '',
                selectedId === agent.id
                  ? 'border-l-[#0b7a75] bg-white'
                  : 'border-l-transparent hover:bg-white/60',
              ].join(' ')}
            >
              <button
                onClick={() => selectAgent(agent.id)}
                disabled={status === 'running'}
                className={[
                  'w-full text-left px-3 pt-3 pb-2.5 disabled:cursor-not-allowed',
                  selectedId === agent.id ? 'text-[#19535f]' : 'text-zinc-600 hover:text-zinc-900',
                ].join(' ')}
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-semibold leading-snug">{agent.label}</span>
                  <span className={`text-[10px] font-mono shrink-0 ${selectedId === agent.id ? 'text-[#0b7a75]' : 'text-zinc-300'}`}>{agent.ticket}</span>
                </div>
                <p className="text-[11px] mt-1 leading-relaxed text-zinc-400 line-clamp-2">{agent.description}</p>
              </button>

              {/* Seed controls — Duplicate Detection only */}
              {agent.id === 'duplicate' && (
                <div className="px-3 pb-3" onClick={e => e.stopPropagation()}>
                  <div className="border-t border-zinc-100 pt-2.5 flex flex-col gap-1.5">
                    <p className="text-[10px] text-zinc-400 font-semibold uppercase tracking-wide">Seed</p>
                    <div className="flex gap-1.5">
                      {SEED_PRESETS.map(preset => (
                        <button
                          key={preset.label}
                          onClick={() => seed(preset.label)}
                          disabled={seedStatus === 'running' || status === 'running'}
                          title={preset.desc}
                          className={`flex-1 rounded-sm border text-white disabled:opacity-30 disabled:cursor-not-allowed py-1.5 text-[11px] font-mono font-semibold tracking-wide transition-all ${preset.color}`}
                        >
                          {seedStatus === 'running' ? '…' : preset.label}
                        </button>
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Cleanup — global footer, applies to all agents */}
        <div className="mt-4 pt-4 border-t border-zinc-200">
          <button
            onClick={cleanup}
            disabled={seedStatus === 'running' || status === 'running'}
            className="w-full rounded-sm border border-zinc-200 hover:border-zinc-300 bg-zinc-50 hover:bg-zinc-100 text-zinc-500 hover:text-zinc-700 disabled:opacity-30 disabled:cursor-not-allowed py-1.5 text-[11px] font-mono font-semibold tracking-wide transition-all"
          >
            {seedStatus === 'running' ? '…' : 'Cleanup'}
          </button>
          <p className="text-[10px] text-zinc-300 mt-1.5 text-center">removes all test data</p>
        </div>
      </div>

      {/* Main panel */}
      <div className="flex-1 flex flex-col min-w-0 overflow-hidden">
      <div className="flex flex-col flex-1 min-h-0 overflow-hidden gap-4">

        {/* Agent header */}
        <div className="shrink-0">
          <div className="flex items-baseline gap-3 mb-1">
            <h2 className="text-lg font-bold text-zinc-900 tracking-tight">{selectedAgent.label}</h2>
            <span className="text-[10px] px-2 py-0.5 rounded-sm bg-zinc-100 text-zinc-400 font-mono tracking-widest shrink-0">
              {selectedAgent.ticket}
            </span>
          </div>
          <p className="text-zinc-500 text-sm leading-snug">{selectedAgent.description}</p>
          {selectedAgent.examples.length > 0 && (
            <div className="flex flex-wrap gap-2 mt-2">
              <span className="text-xs text-zinc-400 self-center">Try:</span>
              {selectedAgent.examples.map(ex => (
                <button
                  key={ex}
                  onClick={() => { if (status !== 'running') setQuestion(ex) }}
                  disabled={status === 'running'}
                  className="text-xs px-2.5 py-1 rounded-sm border border-zinc-200 text-zinc-500 hover:border-[#0b7a75] hover:text-[#0b7a75] transition-colors disabled:opacity-30 bg-white"
                >
                  {ex}
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Prompt row + status */}
        <div className="shrink-0">
          {/* LIVE / LOCAL toggle — above the input */}
          <div className="flex items-center gap-2 mb-2">
            <div className="flex rounded-md border border-zinc-200 overflow-hidden">
              {([
                { key: 'live',  label: 'LIVE',  active: 'bg-[#c0392b] text-white', hover: 'hover:text-[#c0392b]' },
                { key: 'local', label: 'LOCAL', active: 'bg-[#19535f] text-white', hover: 'hover:text-[#19535f]' },
              ] as const).map((m, i) => (
                <>
                  {i > 0 && <div key={`sep-${m.key}`} className="w-px bg-zinc-200" />}
                  <button
                    key={m.key}
                    onClick={() => { if (status !== 'running') setMode(m.key) }}
                    disabled={status === 'running'}
                    className={`px-3 py-1.5 text-[11px] font-bold font-mono tracking-widest transition-all disabled:opacity-40 ${
                      mode === m.key ? m.active : `bg-white text-zinc-400 ${m.hover}`
                    }`}
                  >
                    {m.label}
                  </button>
                </>
              ))}
            </div>
          </div>

          {/* Input + action buttons */}
          <div className="flex gap-2">
            <input
              type="text"
              value={question}
              onChange={e => setQuestion(e.target.value)}
              onKeyDown={e => e.key === 'Enter' && status !== 'running' && run()}
              placeholder={selectedAgent.placeholder || 'Enter a prompt…'}
              disabled={status === 'running'}
              className="flex-1 rounded-md bg-white border border-zinc-300 text-zinc-900 placeholder-zinc-400 px-3 py-2 text-sm focus:outline-none focus:border-[#0b7a75] focus:ring-1 focus:ring-[#0b7a75]/15 disabled:opacity-40 transition-colors"
            />
            {status === 'running' ? (
              <button onClick={stop} className="rounded-md bg-[#7b2d26] hover:bg-[#5e2219] text-white px-4 py-2 text-xs font-bold tracking-widest transition-colors font-mono">
                STOP
              </button>
            ) : (
              <button onClick={run} disabled={!question.trim()} className="rounded-md bg-[#0b7a75] hover:bg-[#19535f] disabled:opacity-25 text-white px-5 py-2 text-xs font-bold tracking-widest transition-colors font-mono">
                RUN
              </button>
            )}
            {events.length > 0 && status !== 'running' && (
              <button onClick={reset} className="rounded-md border border-zinc-200 hover:border-zinc-300 text-zinc-400 hover:text-zinc-600 px-4 py-2 text-xs transition-colors bg-white">
                Clear
              </button>
            )}
          </div>
          <div className="h-5 mt-1.5 flex items-center">
            {status === 'running' && (
              <div className="flex items-center gap-1.5 text-xs text-zinc-400">
                <span className={`w-1.5 h-1.5 rounded-full animate-pulse inline-block ${
                  mode === 'local' ? 'bg-[#19535f]' : 'bg-[#c0392b]'
                }`} />
                {mode === 'local' ? 'Running local…' : 'Running…'}
              </div>
            )}
            {status === 'done' && (() => {
              const usageEvents = events.filter(e => e.type === 'token_usage') as Extract<typeof events[number], { type: 'token_usage' }>[]
              const totalIn  = usageEvents.reduce((s, e) => s + e.input_tokens, 0)
              const totalOut = usageEvents.reduce((s, e) => s + e.output_tokens, 0)
              return (
                <p className="text-[11px] text-zinc-400 font-mono">
                  {events.filter(e => e.type === 'tool_call').length} tool calls
                  {totalIn > 0 && <span className="ml-2">· {totalIn.toLocaleString()} in / {totalOut.toLocaleString()} out tokens</span>}
                  {mode === 'local' && <span className="ml-2 text-[#19535f]">· local</span>}
                </p>
              )
            })()}
            {status === 'error' && (
              <p className="text-xs text-[#7b2d26]">Agent encountered an error</p>
            )}
          </div>
        </div>

        {/* Timeline */}
        <div className="flex-1 min-h-0 rounded-md border border-zinc-200 bg-white overflow-y-auto">
          {events.length > 0 ? (
            <div className="p-4">
              <Timeline events={events} />
              <div ref={bottomRef} />
            </div>
          ) : (
            <div className="flex items-center justify-center h-full">
              <p className="text-[11px] font-mono text-zinc-300 italic">Run an agent to see output</p>
            </div>
          )}
        </div>

      </div>{/* end scrollable column */}

      {/* Bottom seed strip — hidden when idle, expanded while running, collapsed when done */}
      {seedLog.length > 0 && (
        <div className="shrink-0 border-t border-zinc-200 bg-white">
          {seedStatus === 'running' ? (
            <div className="h-[200px] flex flex-col">
              <div className="flex items-center px-4 py-2 border-b border-zinc-100 shrink-0">
                <span className="w-1.5 h-1.5 rounded-full bg-[#0b7a75] animate-pulse mr-2 inline-block" />
                <p className="text-[11px] font-mono text-zinc-400">seeding…</p>
              </div>
              <div className="flex-1 overflow-y-auto px-4 py-3">
                {seedLog.map((block, bi) => (
                  <div key={bi} className="mb-2">
                    {block.phase && (
                      <p className="text-[10px] text-[#0b7a75] font-mono uppercase tracking-widest mb-1">── {block.phase}</p>
                    )}
                    {block.lines.map((line, li) => (
                      <p key={li} className="text-[11px] font-mono text-zinc-500 leading-relaxed whitespace-pre">{line}</p>
                    ))}
                  </div>
                ))}
                <div ref={seedBottomRef} />
              </div>
            </div>
          ) : (
            <div className="flex items-center justify-between px-4 py-2.5">
              <p className={`text-[11px] font-mono ${seedStatus === 'done' ? 'text-[#0b7a75]' : 'text-[#7b2d26]'}`}>
                {seedStatus === 'done' ? '✓ seed complete' : '✕ seed failed'}
              </p>
              <button
                onClick={() => { setSeedLog([]); setSeedStatus('idle') }}
                className="text-[10px] text-zinc-400 hover:text-zinc-600 transition-colors font-mono"
              >
                dismiss
              </button>
            </div>
          )}
        </div>
      )}

      </div>{/* end main panel */}
    </div>
  )
}
