import asyncio
import json
import subprocess
import sys
from datetime import date
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from sse_starlette.sse import EventSourceResponse

from streaming_loop import run_agent_streaming
from local_loop import run_agent_local
from equiflow_data_server import (
    handle_list_orders,
    handle_get_compliance_result,
    handle_get_order,
    handle_get_saga,
    handle_query_audit_log,
    handle_get_ledger_account,
    handle_create_incident,
    handle_list_recent_failures,
    handle_get_service_health,
    handle_get_user_risk_profile,
)
from mcp.types import TextContent
from duplicate_agent   import SYSTEM_TEMPLATE as DUPLICATE_SYSTEM,   TOOLS as DUPLICATE_TOOLS
from compliance_agent  import SYSTEM_TEMPLATE as COMPLIANCE_SYSTEM,  TOOLS as COMPLIANCE_TOOLS
from agent             import SYSTEM_TEMPLATE as TRIAGE_SYSTEM,      TOOLS as TRIAGE_TOOLS
from escalation_agent  import SYSTEM_TEMPLATE as ESCALATION_SYSTEM,  TOOLS as ESCALATION_TOOLS
import playbooks.duplicate   as _pb_duplicate
import playbooks.compliance  as _pb_compliance
import playbooks.triage      as _pb_triage
import playbooks.escalation  as _pb_escalation

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
    "get_order":       handle_get_order,
    "get_saga":        handle_get_saga,
    "list_orders":     handle_list_orders,
    "query_audit_log": handle_query_audit_log,
}

ESCALATION_DISPATCH = {
    "list_orders":           handle_list_orders,
    "get_order":             handle_get_order,
    "get_saga":              handle_get_saga,
    "query_audit_log":       handle_query_audit_log,
    "get_ledger_account":    handle_get_ledger_account,
    "create_incident":       handle_create_incident,
    "list_recent_failures":  handle_list_recent_failures,
    "get_service_health":    handle_get_service_health,
    "get_user_risk_profile": handle_get_user_risk_profile,
}

AGENTS = {
    "duplicate": {
        "system":   DUPLICATE_SYSTEM,
        "tools":    DUPLICATE_TOOLS,
        "dispatch": DUPLICATE_DISPATCH,
    },
    "compliance": {
        "system":   COMPLIANCE_SYSTEM,
        "tools":    COMPLIANCE_TOOLS,
        "dispatch": COMPLIANCE_DISPATCH,
    },
    "triage": {
        "system":   TRIAGE_SYSTEM,
        "tools":    TRIAGE_TOOLS,
        "dispatch": TRIAGE_DISPATCH,
    },
    "escalation": {
        "system":   ESCALATION_SYSTEM,
        "tools":    ESCALATION_TOOLS,
        "dispatch": ESCALATION_DISPATCH,
    },
}

PLAYBOOKS = {
    "duplicate":   _pb_duplicate,
    "compliance":  _pb_compliance,
    "triage":      _pb_triage,
    "escalation":  _pb_escalation,
}

SCRIPT_DIR = Path(__file__).parent


class RunRequest(BaseModel):
    agent: str
    question: str
    mode: str = "local"  # "live" | "local"


SEED_LEVELS = {
    "HIGH": "0.2s",
    "MED":  "2s",
    "LOW":  "7s",
}

class SeedRequest(BaseModel):
    agent: str
    level: str = "HIGH"
    messages: int = 20


@app.post("/api/run")
async def run_agent_endpoint(req: RunRequest):
    config = AGENTS.get(req.agent)
    if not config:
        async def error_gen():
            yield {"data": json.dumps({"type": "error", "message": f"Unknown agent: {req.agent}"})}
        return EventSourceResponse(error_gen())

    dispatch = config["dispatch"]

    async def call_tool(name: str, args: dict) -> str:
        handler = dispatch.get(name)
        if handler is None:
            return f"Unknown tool: {name}"
        results = await handler(args)
        return results[0].text if results else ""

    # ── Local: real DB, rule-based planner, no Anthropic ──────────────────
    if req.mode == "local":
        planner = PLAYBOOKS.get(req.agent)
        if planner is None:
            async def no_planner_gen():
                yield {"data": json.dumps({
                    "type": "error",
                    "message": f"LOCAL mode not yet supported for agent: {req.agent}",
                })}
            return EventSourceResponse(no_planner_gen())

        async def local_gen():
            async for event in run_agent_local(planner.run, call_tool, req.question):
                yield {"data": json.dumps(event)}

        return EventSourceResponse(local_gen())

    # ── Live: real DB + real Anthropic ─────────────────────────────────────
    system   = config["system"].format(today=date.today())
    tools    = config["tools"]

    async def event_generator():
        async for event in run_agent_streaming(system, tools, call_tool, req.question):
            yield {"data": json.dumps(event)}

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
    elif req.agent == "escalation":
        cmd = [sys.executable, "-u", str(SCRIPT_DIR / "seed_failed_orders.py")]
        if req.level == "systemic":
            cmd.append("--systemic")
        elif req.level == "clean":
            cmd.append("--clean")
        elif req.level == "clean-systemic":
            cmd += ["--systemic", "--clean"]
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


@app.get("/api/test-data")
async def get_test_data():
    import asyncio as _asyncio

    statuses = [
        ("FAILED",    "escalation"),
        ("REJECTED",  "compliance"),
        ("PENDING",   "triage"),
        ("OPEN",      "triage"),
        ("FILLED",    "triage"),
        ("CANCELLED", "triage"),
    ]

    results = await _asyncio.gather(
        *[handle_list_orders({"status": s, "size": 10}) for s, _ in statuses]
    )

    grouped: dict = {"escalation": [], "compliance": [], "triage": []}

    for (_, group), res in zip(statuses, results):
        if not res:
            continue
        try:
            data = json.loads(res[0].text)
            for o in data.get("content", []):
                grouped[group].append({
                    "id":     o["id"],
                    "ticker": o.get("ticker", "?"),
                    "status": o.get("status", "?"),
                    "side":   o.get("side", ""),
                })
        except Exception:
            pass

    return grouped
