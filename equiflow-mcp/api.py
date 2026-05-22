import json
from datetime import date

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

DUPLICATE_DISPATCH = {"list_orders": handle_list_orders}
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


class RunRequest(BaseModel):
    agent: str
    question: str


@app.post("/api/run")
async def run_agent_endpoint(req: RunRequest):
    config = AGENTS.get(req.agent)
    if not config:
        async def error_gen():
            yield {"data": json.dumps({"type": "error", "message": f"Unknown agent: {req.agent}"})}
        return EventSourceResponse(error_gen())

    system = config["system"].format(today=date.today())
    tools = config["tools"]
    dispatch = config["dispatch"]

    async def call_tool(name: str, args: dict) -> str:
        handler = dispatch.get(name)
        if handler is None:
            return f"Unknown tool: {name}"
        results = await handler(args)
        return results[0].text if results else ""

    async def event_generator():
        async for event in run_agent_streaming(system, tools, call_tool, req.question):
            yield {"data": json.dumps(event)}

    return EventSourceResponse(event_generator())


@app.get("/api/agents")
async def list_agents():
    return {"agents": list(AGENTS.keys())}
