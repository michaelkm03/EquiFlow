import { useState, useRef } from 'react'
import type { AgentEvent } from '../types'
import { StepCard } from './StepCard'

const AGENTS = [
  { id: 'duplicate', label: 'Duplicate Detection', placeholder: 'Check for duplicate orders today' },
  { id: 'compliance', label: 'Compliance Monitor', placeholder: 'Show all compliance breaches this week' },
  { id: 'triage', label: 'Order Triage', placeholder: 'Triage order <paste UUID here>' },
]

type Status = 'idle' | 'running' | 'done' | 'error'

export function AgentRunner() {
  const [agent, setAgent] = useState(AGENTS[0].id)
  const [question, setQuestion] = useState('')
  const [events, setEvents] = useState<AgentEvent[]>([])
  const [status, setStatus] = useState<Status>('idle')
  const bottomRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<(() => void) | null>(null)

  const selectedAgent = AGENTS.find(a => a.id === agent)!

  function scrollToBottom() {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }

  async function run() {
    if (!question.trim()) return
    setEvents([])
    setStatus('running')

    const res = await fetch('/api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ agent, question }),
    })

    if (!res.ok || !res.body) {
      setStatus('error')
      return
    }

    const reader = res.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    abortRef.current = () => reader.cancel()

    // eslint-disable-next-line no-constant-condition
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
    <div className="flex flex-col h-screen max-w-4xl mx-auto p-6 gap-4">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white tracking-tight">EquiFlow Agent Runner</h1>
        <p className="text-slate-400 text-sm mt-1">Watch agents reason, call tools, and report findings in real time</p>
      </div>

      {/* Controls */}
      <div className="flex gap-3 flex-wrap">
        <select
          value={agent}
          onChange={e => { setAgent(e.target.value); reset() }}
          disabled={status === 'running'}
          className="rounded-lg bg-slate-800 border border-slate-700 text-slate-200 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-50"
        >
          {AGENTS.map(a => (
            <option key={a.id} value={a.id}>{a.label}</option>
          ))}
        </select>

        <input
          type="text"
          value={question}
          onChange={e => setQuestion(e.target.value)}
          onKeyDown={e => e.key === 'Enter' && status !== 'running' && run()}
          placeholder={selectedAgent.placeholder}
          disabled={status === 'running'}
          className="flex-1 min-w-64 rounded-lg bg-slate-800 border border-slate-700 text-slate-200 placeholder-slate-500 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-violet-500 disabled:opacity-50"
        />

        {status === 'running' ? (
          <button
            onClick={stop}
            className="rounded-lg bg-red-600 hover:bg-red-500 text-white px-4 py-2 text-sm font-medium transition-colors"
          >
            Stop
          </button>
        ) : (
          <button
            onClick={run}
            disabled={!question.trim()}
            className="rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-40 text-white px-4 py-2 text-sm font-medium transition-colors"
          >
            Run
          </button>
        )}

        {events.length > 0 && status !== 'running' && (
          <button
            onClick={reset}
            className="rounded-lg border border-slate-700 hover:border-slate-500 text-slate-400 hover:text-slate-200 px-4 py-2 text-sm transition-colors"
          >
            Clear
          </button>
        )}
      </div>

      {/* Status bar */}
      {status === 'running' && (
        <div className="flex items-center gap-2 text-sm text-violet-300">
          <span className="inline-block w-2 h-2 rounded-full bg-violet-400 animate-pulse" />
          Agent running...
        </div>
      )}
      {status === 'done' && (
        <div className="text-sm text-emerald-400">✓ Completed — {events.length} steps</div>
      )}
      {status === 'error' && (
        <div className="text-sm text-red-400">Agent encountered an error</div>
      )}

      {/* Timeline */}
      <div className="flex-1 overflow-y-auto flex flex-col gap-3 pb-4">
        {events.length === 0 && status === 'idle' && (
          <div className="flex-1 flex items-center justify-center text-slate-600 text-sm">
            Select an agent, enter a question, and press Run
          </div>
        )}
        {events.map((event, i) => (
          <StepCard key={i} event={event} />
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
