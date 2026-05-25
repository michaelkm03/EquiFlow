# Run Modes

Two execution modes are available via the LIVE / LOCAL toggle in the UI.

## Mode Overview

| | **LIVE** | **LOCAL** |
|---|---|---|
| **Anthropic API** | Yes — real calls | No |
| **Database** | Real | Real |
| **Results** | Real, LLM-reasoned | Real, rule-based planner |
| **Cost** | Tokens per run | Free |
| **Data freshness** | Reflects current DB | Reflects current DB |
| **Requires** | API credits + DB up | DB up |
| **Use for** | Demos, production parity, verifying LLM reasoning | Day-to-day dev and testing against seeded data |
| **Don't use for** | Cost-sensitive iteration | Edge cases not in the decision rules |
| **Natural language synthesis** | Yes — LLM prose | No — structured summary only |

## Per-Agent Support

| Agent | LIVE | LOCAL |
|---|---|---|
| **Duplicate Detection** | Full support | Full support — deterministic algorithm (group by fields, compute time gaps) |
| **Compliance Monitor** | Full support | Full support — rule-based planner lists REJECTED orders and groups violations |
| **Order Triage** | Full support | Full support — rule-based planner fetches order → saga → audit log and applies failure-type rules |
| **Failure Escalation** | Full support (EQ-132) | Planned (EQ-139) |

## How LOCAL Works

LOCAL replaces the Anthropic API call with a **per-agent planner** — a Python async function that encodes the same decision logic from the system prompt, calls real tool handlers, and emits the identical SSE event stream.

Planners live in `playbooks/`:

```
equiflow-mcp/
  local_loop.py              thin wrapper: drives the planner, same interface as streaming_loop.py
  playbooks/
    base.py                  shared helpers: UUID extraction, date-range parsing
    duplicate.py             group-by + gap calculation
    compliance.py            iterate REJECTED orders → group violations → find repeat offenders
    triage.py                get_order → get_saga → query_audit_log → match failure rules
```

Each planner exposes `async def run(question, call_tool)` and yields the same event types as `streaming_loop.py`:

| Event | Fields |
|---|---|
| `iteration_start` | `iteration: int` |
| `tool_call` | `name: str`, `input: dict` |
| `tool_result` | `name: str`, `result: str` |
| `done` | `answer: str` |
| `error` | `message: str` |

`token_usage` is not emitted in LOCAL mode — no tokens are consumed.

## When LOCAL Fidelity Is Lower Than LIVE

LOCAL planners implement the **happy-path decision rules** from the system prompt. The LLM handles cases they don't:

- Ambiguous or unexpected saga states not in the spec
- Tool results with malformed or partial data
- Edge cases that require reasoning across multiple tool results simultaneously
- Natural language synthesis — LOCAL answers are structured summaries, not fluent prose

For development and testing these gaps are acceptable. For production or demos, use LIVE.

## Event Schema

Both modes emit the same event types with identical field shapes. The frontend cannot distinguish LOCAL from LIVE by event structure — only the absence of `token_usage` events and the `· local` status badge differ.
