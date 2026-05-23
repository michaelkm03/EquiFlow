import { useState } from 'react'
import type { AgentEvent } from '../types'

type ToolCall = Extract<AgentEvent, { type: 'tool_call' }>
type ToolResult = Extract<AgentEvent, { type: 'tool_result' }>
type Step = ToolCall | ToolResult
type IterGroup = { iteration: number; steps: Step[] }
type Terminal = Extract<AgentEvent, { type: 'done' }> | Extract<AgentEvent, { type: 'error' }>

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
    <pre className="mt-2 rounded-md bg-black/30 p-3 text-xs text-slate-300 overflow-x-auto whitespace-pre-wrap break-words max-h-64 overflow-y-auto">
      {text}
    </pre>
  )
}

function StepRow({ step, isLast }: { step: Step; isLast: boolean }) {
  const [open, setOpen] = useState(false)
  const isCall = step.type === 'tool_call'

  return (
    <div className="mb-1.5">
      <div className="flex items-center gap-2 text-xs">
        <span className="text-slate-600 font-mono select-none leading-none">{isLast ? '└─' : '├─'}</span>
        {isCall ? (
          <code className="text-violet-200 bg-violet-500/10 border border-violet-500/20 px-1.5 py-0.5 rounded font-mono text-[11px]">
            {(step as ToolCall).name}
          </code>
        ) : (
          <>
            <span className="text-emerald-400 text-[11px]">✓</span>
            <span className="text-slate-500 font-mono text-[11px]">{(step as ToolResult).name}</span>
          </>
        )}
        <button
          onClick={() => setOpen(o => !o)}
          className="text-[10px] text-slate-600 hover:text-slate-400 border border-slate-700/60 hover:border-slate-600 rounded px-1.5 py-0.5 transition-colors"
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

export function Timeline({ events }: { events: AgentEvent[] }) {
  const { iterations, terminal } = groupEvents(events)
  if (iterations.length === 0 && !terminal) return null

  const lineFollows = (gi: number) => gi < iterations.length - 1 || terminal !== null

  return (
    <div>
      {iterations.map((group, gi) => (
        <div key={group.iteration} className="flex">
          {/* Left rail column */}
          <div className="flex flex-col items-center w-6 shrink-0 mr-4">
            <div className="w-6 h-6 rounded-full border-2 border-blue-500 bg-slate-900 flex items-center justify-center shrink-0 z-10">
              <span className="text-[10px] font-bold text-blue-400 leading-none">{group.iteration}</span>
            </div>
            {lineFollows(gi) && <div className="w-px flex-1 bg-slate-700/50 my-1" />}
          </div>
          {/* Content */}
          <div className={`flex-1 min-w-0 ${lineFollows(gi) ? 'pb-3' : 'pb-1'}`}>
            <p className="text-[11px] font-semibold text-blue-400 uppercase tracking-wider mb-2">
              Iteration {group.iteration}
            </p>
            {group.steps.length > 0
              ? group.steps.map((step, si) => (
                  <StepRow key={si} step={step} isLast={si === group.steps.length - 1} />
                ))
              : <span className="text-xs text-slate-600 italic">thinking…</span>
            }
          </div>
        </div>
      ))}

      {terminal && (
        <div className="flex">
          {/* Terminal node */}
          <div className="w-6 shrink-0 mr-4">
            {terminal.type === 'done' ? (
              <div className="w-6 h-6 rounded-full border-2 border-amber-500 bg-slate-900 flex items-center justify-center">
                <span className="text-[11px] text-amber-400 leading-none">★</span>
              </div>
            ) : (
              <div className="w-6 h-6 rounded-full border-2 border-red-500 bg-slate-900 flex items-center justify-center">
                <span className="text-[11px] text-red-400 leading-none">✕</span>
              </div>
            )}
          </div>
          {/* Terminal content */}
          <div className="flex-1 min-w-0 pt-0.5 pb-2">
            {terminal.type === 'done' ? (
              <>
                <p className="text-[11px] font-semibold text-amber-400 uppercase tracking-wider mb-2">
                  Final Answer
                </p>
                <div className="whitespace-pre-wrap text-slate-200 text-sm leading-relaxed">
                  {terminal.answer}
                </div>
              </>
            ) : (
              <>
                <p className="text-[11px] font-semibold text-red-400 uppercase tracking-wider mb-1">Error</p>
                <p className="text-sm text-red-300">{terminal.message}</p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
