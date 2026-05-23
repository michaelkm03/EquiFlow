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
    <pre className="mt-2 rounded-sm bg-zinc-950 border border-zinc-800/60 p-3 text-[11px] text-zinc-500 overflow-x-auto whitespace-pre-wrap break-words max-h-60 overflow-y-auto font-mono leading-relaxed">
      {text}
    </pre>
  )
}

function StepRow({ step, isLast }: { step: Step; isLast: boolean }) {
  const [open, setOpen] = useState(false)
  const isCall = step.type === 'tool_call'

  return (
    <div className="mb-1.5">
      <div className="flex items-center gap-2">
        <span className="text-zinc-800 font-mono select-none text-[11px] leading-none">{isLast ? '└─' : '├─'}</span>
        {isCall ? (
          <code className="text-green-300/70 bg-green-500/[0.06] border border-green-500/[0.12] px-1.5 py-0.5 rounded-sm font-mono text-[11px]">
            {(step as ToolCall).name}
          </code>
        ) : (
          <>
            <span className="text-green-500 text-[11px]">✓</span>
            <span className="text-zinc-600 font-mono text-[11px]">{(step as ToolResult).name}</span>
          </>
        )}
        <button
          onClick={() => setOpen(o => !o)}
          className="text-[10px] text-zinc-700 hover:text-zinc-400 border border-zinc-800 hover:border-zinc-700 rounded-sm px-1.5 py-0.5 transition-colors font-mono"
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
          {/* Rail column */}
          <div className="flex flex-col items-center w-6 shrink-0 mr-4">
            <div className="w-5 h-5 rounded-full border border-zinc-700 bg-zinc-950 flex items-center justify-center shrink-0 z-10">
              <span className="text-[9px] font-semibold text-zinc-500 leading-none font-mono">{group.iteration}</span>
            </div>
            {lineFollows(gi) && <div className="w-px flex-1 bg-zinc-800/70 my-1" />}
          </div>
          {/* Content */}
          <div className={`flex-1 min-w-0 ${lineFollows(gi) ? 'pb-4' : 'pb-1'}`}>
            <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-widest mb-2 font-mono">
              Step {group.iteration}
            </p>
            {group.steps.length > 0
              ? group.steps.map((step, si) => (
                  <StepRow key={si} step={step} isLast={si === group.steps.length - 1} />
                ))
              : <span className="text-[11px] text-zinc-700 italic">thinking…</span>
            }
          </div>
        </div>
      ))}

      {terminal && (
        <div className="flex">
          {/* Terminal node */}
          <div className="w-6 shrink-0 mr-4">
            {terminal.type === 'done' ? (
              <div className="w-5 h-5 rounded-full border border-zinc-600 bg-zinc-950 flex items-center justify-center">
                <span className="text-[10px] text-zinc-300 leading-none">★</span>
              </div>
            ) : (
              <div className="w-5 h-5 rounded-full border border-red-500/40 bg-zinc-950 flex items-center justify-center">
                <span className="text-[10px] text-red-400 leading-none">✕</span>
              </div>
            )}
          </div>
          {/* Terminal content */}
          <div className="flex-1 min-w-0 pt-0.5 pb-2">
            {terminal.type === 'done' ? (
              <>
                <p className="text-[10px] font-semibold text-zinc-600 uppercase tracking-widest mb-3 font-mono">
                  Result
                </p>
                <div className="whitespace-pre-wrap text-zinc-200 text-sm leading-relaxed">
                  {terminal.answer}
                </div>
              </>
            ) : (
              <>
                <p className="text-[10px] font-semibold text-red-500 uppercase tracking-widest mb-1 font-mono">Error</p>
                <p className="text-sm text-red-400">{terminal.message}</p>
              </>
            )}
          </div>
        </div>
      )}
    </div>
  )
}
