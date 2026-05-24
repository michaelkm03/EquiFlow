"""
Traces the saga and audit trail of a specific failed or stuck order
to pinpoint root cause and recommend a concrete action.
"""
import asyncio
import sys
from datetime import date

from loop import run_agent
from equiflow_data_server import (
    handle_get_order,
    handle_list_orders,
    handle_get_saga,
    handle_query_audit_log,
)

# ---------------------------------------------------------------------------
# EquiFlow wiring
# ---------------------------------------------------------------------------

SYSTEM_TEMPLATE = """
You are an EquiFlow order triage agent. Today's date is {today}.

Your goal: given a stuck or failed order, identify the root cause and explain
it in plain English so an on-call engineer can act immediately.

Your final response must state:
- Which service failed
- The failure reason (exact string from the data)
- How many retries have occurred
- A concrete recommendation: retry, escalate, or investigate further

Do not speculate beyond what the tools return. If data is missing, say so.
"""

TOOLS = [
    {
        "name": "get_order",
        "description": (
            "Get a single EquiFlow order by UUID. "
            "Returns current status, order type, ticker, quantity, saga ID, and timestamps. "
            "Use this first when investigating a stuck or failed order."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order to retrieve"}
            },
            "required": ["order_id"],
        },
    },
    {
        "name": "list_orders",
        "description": (
            "List orders from the EquiFlow database with optional filtering. "
            "Supports filtering by status, ticker symbol, and date range. "
            "Returns paginated results sorted by createdAt descending. "
            "Use this to find a real order_id before calling get_order or query_audit_log."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "status": {
                    "type": "string",
                    "enum": [
                        "PENDING", "COMPLIANCE_CHECK", "OPEN", "FILLED",
                        "PARTIALLY_FILLED", "CANCELLED", "REJECTED",
                        "FAILED", "PENDING_TRIGGER", "TRIGGERED",
                    ],
                    "description": "Filter by order status",
                },
                "ticker": {"type": "string", "description": "Filter by ticker symbol (e.g. AAPL)"},
                "from":   {"type": "string", "description": "Start date filter in YYYY-MM-DD format"},
                "to":     {"type": "string", "description": "End date filter in YYYY-MM-DD format"},
                "page":   {"type": "integer", "description": "Page number (0-based, default 0)"},
                "size":   {"type": "integer", "description": "Page size (default 25)"},
            },
            "required": [],
        },
    },
    {
        "name": "get_saga",
        "description": (
            "Get the saga execution trace for a distributed transaction by saga UUID. "
            "Returns each saga step with its status, failure reason, and retry count. "
            "Use after get_order to identify which step in the transaction failed and why."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "saga_id": {"type": "string", "description": "UUID of the saga to retrieve"}
            },
            "required": ["saga_id"],
        },
    },
    {
        "name": "query_audit_log",
        "description": (
            "Get the full append-only audit trail for a specific order by UUID. "
            "Returns every state transition, retry attempt, and timestamp in chronological order. "
            "Use after get_saga to determine how many retries occurred and when the last attempt happened."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order to retrieve audit events for"}
            },
            "required": ["order_id"],
        },
    },
]

DISPATCH = {
    "get_order":       handle_get_order,
    "list_orders":     handle_list_orders,
    "get_saga":        handle_get_saga,
    "query_audit_log": handle_query_audit_log,
}


async def call_tool(name: str, args: dict) -> str:
    handler = DISPATCH.get(name)
    if handler is None:
        return f"Unknown tool: {name}"
    results = await handler(args)
    return results[0].text if results else ""


# ---------------------------------------------------------------------------
# CLI entry point
# ---------------------------------------------------------------------------

async def main():
    system = SYSTEM_TEMPLATE.format(today=date.today())
    question = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else input("Question: ")
    print()
    answer = await run_agent(system, TOOLS, call_tool, question)
    print(f"\n{answer}")


if __name__ == "__main__":
    asyncio.run(main())
