import asyncio
import json
import subprocess
import sys
import time
from datetime import date
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from streaming_loop import run_agent_streaming
from equiflow_data_server import (
    handle_list_orders,
    handle_get_compliance_result,
    handle_get_order,
    handle_get_saga,
    handle_query_audit_log,
)
from mcp.types import TextContent
from duplicate_agent import SYSTEM_TEMPLATE as DUPLICATE_SYSTEM, TOOLS as DUPLICATE_TOOLS
from compliance_agent import SYSTEM_TEMPLATE as COMPLIANCE_SYSTEM, TOOLS as COMPLIANCE_TOOLS
from agent import SYSTEM_TEMPLATE as TRIAGE_SYSTEM, TOOLS as TRIAGE_TOOLS

app = FastAPI(title="EquiFlow Agent API")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ---------------------------------------------------------------------------
# Tool dispatch per agent
# ---------------------------------------------------------------------------

# Only the fields the duplicate agent needs — strips sagaId, updatedAt, version, etc.
_DUPE_FIELDS = {"id", "userId", "ticker", "side", "quantity", "limitPrice", "type", "createdAt"}

async def _list_orders_slim(args: dict) -> list[TextContent]:
    results = await handle_list_orders(args)
    if not results:
        return results
    try:
        data = json.loads(results[0].text)
        if isinstance(data, dict) and "content" in data:
            slim_orders = []
            for order in data["content"]:
                slim = {k: v for k, v in order.items() if k in _DUPE_FIELDS}
                if slim.get("createdAt"):
                    slim["createdAt"] = slim["createdAt"][:19]  # drop sub-second precision
                slim_orders.append(slim)
            slim_data = {
                "content": slim_orders,
                "last": data.get("last", True),
                "totalElements": data.get("totalElements", len(slim_orders)),
            }
            return [TextContent(type="text", text=json.dumps(slim_data, separators=(",", ":")))]
    except Exception:
        pass
    return results

DUPLICATE_DISPATCH = {"list_orders": _list_orders_slim}
COMPLIANCE_DISPATCH = {
    "list_orders": handle_list_orders,
    "get_compliance_result": handle_get_compliance_result,
}
TRIAGE_DISPATCH = {
    "get_order": handle_get_order,
    "get_saga": handle_get_saga,
    "list_orders": handle_list_orders,
    "query_audit_log": handle_query_audit_log,
}

AGENTS = {
    "duplicate": {
        "system": DUPLICATE_SYSTEM,
        "tools": DUPLICATE_TOOLS,
        "dispatch": DUPLICATE_DISPATCH,
    },
    "compliance": {
        "system": COMPLIANCE_SYSTEM,
        "tools": COMPLIANCE_TOOLS,
        "dispatch": COMPLIANCE_DISPATCH,
    },
    "triage": {
        "system": TRIAGE_SYSTEM,
        "tools": TRIAGE_TOOLS,
        "dispatch": TRIAGE_DISPATCH,
    },
}


SCRIPT_DIR = Path(__file__).parent
FIXTURES_DIR = SCRIPT_DIR / "fixtures"


class RunRequest(BaseModel):
    agent: str
    question: str
    mode: str = "live"  # "live" | "local" | "mock"


SEED_LEVELS = {
    "HIGH": "0.2s",
    "MED":  "2s",
    "LOW":  "7s",
}

class SeedRequest(BaseModel):
    agent: str
    level: str = "HIGH"
    messages: int = 20


async def _local_duplicate_gen(question: str):
    """
    Duplicate detection in pure Python — hits the real DB, skips Anthropic entirely.
    Streams the same event types as a real agent run.
    """
    from collections import defaultdict
    from datetime import datetime as _dt

    today = str(date.today())
    call_input = {"from": today, "to": today, "size": 100}

    yield {"type": "iteration_start", "iteration": 1}
    yield {"type": "tool_call", "name": "list_orders", "input": call_input}

    all_orders: list[dict] = []
    page = 0
    while True:
        results = await _list_orders_slim({**call_input, "page": page})
        result_text = results[0].text if results else "{}"
        yield {"type": "tool_result", "name": "list_orders", "result": result_text}
        try:
            data = json.loads(result_text)
            all_orders.extend(data.get("content", []))
            if data.get("last", True):
                break
            page += 1
            yield {"type": "tool_call", "name": "list_orders", "input": {**call_input, "page": page}}
        except Exception:
            break

    yield {"type": "iteration_start", "iteration": 2}

    # Group orders by duplicate-key fields
    groups: dict[tuple, list] = defaultdict(list)
    for o in all_orders:
        key = (o.get("userId",""), o.get("ticker",""), o.get("side",""),
               str(o.get("quantity","")), str(o.get("limitPrice","")), o.get("type",""))
        groups[key].append(o)

    pairs = []
    for orders in groups.values():
        if len(orders) < 2:
            continue
        orders.sort(key=lambda o: o.get("createdAt", ""))
        for i in range(len(orders) - 1):
            orig, dup = orders[i], orders[i + 1]
            try:
                gap_s = round((_dt.fromisoformat(dup["createdAt"]) -
                               _dt.fromisoformat(orig["createdAt"])).total_seconds(), 2)
            except Exception:
                gap_s = 0.0
            suspicion = "HIGH" if gap_s < 1 else ("MEDIUM" if gap_s <= 5 else "LOW")
            pairs.append({
                "orig_id": orig["id"], "dup_id": dup["id"],
                "user_id": orig.get("userId", ""),
                "ticker":  orig.get("ticker", ""), "side": orig.get("side", ""),
                "qty":     orig.get("quantity", ""), "price": orig.get("limitPrice", ""),
                "gap_s":   gap_s, "suspicion": suspicion,
            })

    suspicions = {p["suspicion"] for p in pairs}
    verdict = "ESCALATE" if "HIGH" in suspicions else ("REVIEW" if suspicions else "CLEAR")

    findings = {"verdict": verdict, "total_orders": len(all_orders), "pairs": pairs}
    summary = f"{len(pairs)} duplicate pair{'s' if len(pairs) != 1 else ''} found" if pairs else "No duplicate pairs found"
    answer = (f"Scanned {len(all_orders)} orders for {today}. {summary}.\n\n"
              f"<findings_json>\n{json.dumps(findings, separators=(',',':'))}\n</findings_json>")

    yield {"type": "done", "answer": answer}


@app.post("/api/run")
async def run_agent_endpoint(req: RunRequest):
    config = AGENTS.get(req.agent)
    if not config:
        async def error_gen():
            yield {"data": json.dumps({"type": "error", "message": f"Unknown agent: {req.agent}"})}
        return EventSourceResponse(error_gen())

    # ── Mock: replay recorded fixture ──────────────────────────────────────
    if req.mode == "mock":
        fixture_path = FIXTURES_DIR / f"{req.agent}.jsonl"
        if not fixture_path.exists():
            async def no_fixture_gen():
                yield {"data": json.dumps({
                    "type": "error",
                    "message": "No recording found — run once in LIVE mode first.",
                })}
            return EventSourceResponse(no_fixture_gen())

        async def replay_gen():
            entries = [json.loads(l) for l in fixture_path.read_text(encoding="utf-8").splitlines() if l.strip()]
            last_t = 0.0
            for entry in entries:
                gap = min(entry["t"] - last_t, 2.0)
                if gap > 0.01:
                    await asyncio.sleep(gap)
                last_t = entry["t"]
                yield {"data": json.dumps(entry["event"])}

        return EventSourceResponse(replay_gen())

    # ── Local: real DB, Python logic, no Anthropic ─────────────────────────
    if req.mode == "local":
        if req.agent != "duplicate":
            async def no_local_gen():
                yield {"data": json.dumps({
                    "type": "error",
                    "message": f"LOCAL mode is only supported for the Duplicate Detection agent.",
                })}
            return EventSourceResponse(no_local_gen())

        async def local_gen():
            async for event in _local_duplicate_gen(req.question):
                yield {"data": json.dumps(event)}

        return EventSourceResponse(local_gen())

    # ── Live: real DB + real Anthropic, auto-saves fixture ─────────────────
    system = config["system"].format(today=date.today())
    tools  = config["tools"]
    dispatch = config["dispatch"]

    async def call_tool(name: str, args: dict) -> str:
        handler = dispatch.get(name)
        if handler is None:
            return f"Unknown tool: {name}"
        results = await handler(args)
        return results[0].text if results else ""

    async def event_generator():
        FIXTURES_DIR.mkdir(exist_ok=True)
        fixture_path = FIXTURES_DIR / f"{req.agent}.jsonl"
        start = time.monotonic()
        lines: list[str] = []
        async for event in run_agent_streaming(system, tools, call_tool, req.question):
            t = round(time.monotonic() - start, 3)
            lines.append(json.dumps({"t": t, "event": event}))
            yield {"data": json.dumps(event)}
        fixture_path.write_text("\n".join(lines), encoding="utf-8")

    return EventSourceResponse(event_generator())


def _run_script_streaming(cmd: list, label: str, loop: asyncio.AbstractEventLoop):
    """Run a subprocess and yield SSE-shaped dicts via an async queue."""
    queue: asyncio.Queue = asyncio.Queue()

    def run_proc():
        proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
        for raw_line in proc.stdout:
            text = raw_line.decode(errors="replace").rstrip()
            if text:
                loop.call_soon_threadsafe(queue.put_nowait, ("line", text))
        rc = proc.wait()
        loop.call_soon_threadsafe(queue.put_nowait, ("done", rc))

    return queue, loop.run_in_executor(None, run_proc)


@app.post("/api/cleanup")
async def cleanup_endpoint():
    cmd = [sys.executable, "-u", str(SCRIPT_DIR / "cleanup_scenario.py"), "--execute"]

    async def cleanup_generator():
        loop = asyncio.get_running_loop()
        yield {"data": json.dumps({"type": "phase", "label": "Cleanup"})}
        queue, fut = _run_script_streaming(cmd, "Cleanup", loop)
        while True:
            kind, value = await queue.get()
            if kind == "line":
                yield {"data": json.dumps({"type": "log", "line": value})}
            else:
                await fut
                if value != 0:
                    yield {"data": json.dumps({"type": "error", "message": f"Cleanup failed (exit {value})"})}
                    return
                break
        yield {"data": json.dumps({"type": "done"})}

    return EventSourceResponse(cleanup_generator())


@app.post("/api/seed")
async def seed_agent_endpoint(req: SeedRequest):
    if req.agent == "duplicate":
        gap = SEED_LEVELS.get(req.level, SEED_LEVELS["HIGH"])
        duration_ms = max(req.messages * 300, 3000)
        cmd = [
            sys.executable, "-u", str(SCRIPT_DIR / "seed_duplicate_orders.py"),
            "--messages", str(req.messages),
            "--duration", str(duration_ms),
            "--duplicate-delay", gap,
        ]
    else:
        async def no_seed_gen():
            yield {"data": json.dumps({"type": "error", "message": f"No seed script for agent: {req.agent}"})}
        return EventSourceResponse(no_seed_gen())

    async def seed_generator():
        loop = asyncio.get_running_loop()
        yield {"data": json.dumps({"type": "phase", "label": "Seed"})}
        queue, fut = _run_script_streaming(cmd, "Seed", loop)
        while True:
            kind, value = await queue.get()
            if kind == "line":
                yield {"data": json.dumps({"type": "log", "line": value})}
            else:
                await fut
                if value != 0:
                    yield {"data": json.dumps({"type": "error", "message": f"Seed failed (exit {value})"})}
                    return
                break
        yield {"data": json.dumps({"type": "done"})}

    return EventSourceResponse(seed_generator())


@app.get("/api/agents")
async def list_agents():
    return {"agents": list(AGENTS.keys())}
