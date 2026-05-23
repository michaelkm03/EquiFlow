export type AgentEvent =
  | { type: 'iteration_start'; iteration: number }
  | { type: 'tool_call'; name: string; input: Record<string, unknown> }
  | { type: 'tool_result'; name: string; result: string }
  | { type: 'done'; answer: string }
  | { type: 'error'; message: string }

export type SeedEvent =
  | { type: 'phase'; label: string }
  | { type: 'log'; line: string }
  | { type: 'done' }
  | { type: 'error'; message: string }
