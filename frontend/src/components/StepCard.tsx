import { useState } from 'react'
import type { AgentEvent } from '../types'

interface Props {
  event: AgentEvent
}

const ICONS: Record<AgentEvent['type'], string> = {
  iteration_start: '🔄',
  tool_call: '🔧',
  tool_result: '✅',
  done: '📋',
  error: '❌',
}

const COLORS: Record<AgentEvent['type'], string> = {
  iteration_start: 'border-blue-500/40 bg-blue-500/5',
  tool_call: 'border-violet-500/40 bg-violet-500/5',
  tool_result: 'border-emerald-500/40 bg-emerald-500/5',
  done: 'border-amber-500/40 bg-amber-500/10',
  error: 'border-red-500/40 bg-red-500/10',
}

function JsonBlock({ value }: { value: string | Record<string, unknown> }) {
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2)
  return (
    <pre className="mt-2 rounded-md bg-black/30 p-3 text-xs text-slate-300 overflow-x-auto whitespace-pre-wrap break-words max-h-64 overflow-y-auto">
      {text}
    </pre>
  )
}

function Collapsible({ label, children }: { label: string; children: React.ReactNode }) {
  const [open, setOpen] = useState(false)
  return (
    <div className="mt-2">
      <button
        onClick={() => setOpen(o => !o)}
        className="text-xs text-slate-400 hover:text-slate-200 flex items-center gap-1 transition-colors"
      >
        <span>{open ? '▾' : '▸'}</span>
        {label}
      </button>
      {open && children}
    </div>
  )
}

export function StepCard({ event }: Props) {
  const icon = ICONS[event.type]
  const color = COLORS[event.type]

  return (
    <div className={`rounded-lg border px-4 py-3 text-sm ${color}`}>
      {event.type === 'iteration_start' && (
        <p className="font-semibold text-blue-300">
          {icon} Iteration {event.iteration}
        </p>
      )}

      {event.type === 'tool_call' && (
        <div>
          <p className="font-semibold text-violet-300">
            {icon} Tool called: <code className="ml-1 rounded bg-black/30 px-1.5 py-0.5 text-violet-200">{event.name}</code>
          </p>
          <Collapsible label="View input">
            <JsonBlock value={event.input} />
          </Collapsible>
        </div>
      )}

      {event.type === 'tool_result' && (
        <div>
          <p className="font-semibold text-emerald-300">
            {icon} Result: <span className="font-normal text-slate-400">{event.name}</span>
          </p>
          <Collapsible label="View result">
            <JsonBlock value={event.result} />
          </Collapsible>
        </div>
      )}

      {event.type === 'done' && (
        <div>
          <p className="font-semibold text-amber-300 mb-2">{icon} Final Answer</p>
          <div className="whitespace-pre-wrap text-slate-200 text-sm leading-relaxed">
            {event.answer}
          </div>
        </div>
      )}

      {event.type === 'error' && (
        <p className="text-red-300">
          {icon} Error: {event.message}
        </p>
      )}
    </div>
  )
}
