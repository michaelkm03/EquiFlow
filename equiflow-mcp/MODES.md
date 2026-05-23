# Run Modes

Three execution modes are available via the LIVE / LOCAL / MOCK toggle in the UI.

## Mode Overview

| | **LIVE** | **LOCAL** | **MOCK** |
|---|---|---|---|
| **Anthropic API** | Yes — real calls | No | No |
| **Database** | Real | Real | No |
| **Results** | Real, LLM-reasoned | Real, Python-computed | Static fixture |
| **Cost** | Tokens per run | Free | Free |
| **Data freshness** | Reflects current DB | Reflects current DB | Always same fixture |
| **Requires** | API credits + DB up | DB up | Nothing |
| **Use for** | Verifying LLM behavior, demos, production parity | Day-to-day dev, testing against seeded data | Pure UI work, no backend needed |
| **Don't use for** | Cost-sensitive iteration | Agents requiring real reasoning | Anything where DB state matters |
| **Accuracy** | Ground truth | Exact for deterministic agents | Approximate (frozen) |

## Per-Agent Support

| Agent | LIVE | LOCAL | MOCK |
|---|---|---|---|
| **Duplicate Detection** | Full support | Full support — task is deterministic (group by fields, compute time gaps) | Supported — fixture at `fixtures/duplicate.jsonl` |
| **Compliance Monitor** | Full support | Not supported — requires LLM reasoning to interpret violation types and weight severity | Supported — fixture at `fixtures/compliance.jsonl` (must record first) |
| **Order Triage** | Full support | Not supported — requires LLM reasoning to interpret saga states, audit trails, and recommend action | Supported — fixture at `fixtures/triage.jsonl` (must record first) |

## Why LOCAL only works for Duplicate Detection

Duplicate detection is a deterministic algorithm:
1. Fetch all orders for the date range
2. Group by `(userId, ticker, side, quantity, limitPrice, type)`
3. For each group with >1 order, compute the time gap and assign suspicion:
   - `< 5 s` → HIGH
   - `5–30 s` → MEDIUM
   - `> 30 s` → LOW
4. Emit `ESCALATE / REVIEW / CLEAR` verdict

No judgment is required — the same inputs always produce the same output. Python is sufficient.

Compliance Monitor and Order Triage involve reasoning: interpreting ambiguous saga failure states, weighing multiple violation types, deciding which orders to investigate further. These require an LLM.

## Mock Fixture Format

Fixtures are stored as JSONL in `fixtures/{agent}.jsonl`. Each line:

```json
{"t": 1.23, "event": {"type": "tool_call", "name": "list_orders", "input": {...}}}
```

`t` = seconds since run start. The replay engine caps gaps at 2 s to keep playback snappy.

LIVE runs auto-save a fresh fixture on completion. To manually create or override a fixture, run the generation script or write JSONL lines matching the event schema in `streaming_loop.py`.

## Event Schema

All modes emit the same event types:

| Event | Fields |
|---|---|
| `iteration_start` | `iteration: int` |
| `tool_call` | `name: str`, `input: dict` |
| `tool_result` | `name: str`, `result: str` |
| `token_usage` | `iteration: int`, `input_tokens: int`, `output_tokens: int` (LIVE only) |
| `done` | `answer: str` |
| `error` | `message: str` |
