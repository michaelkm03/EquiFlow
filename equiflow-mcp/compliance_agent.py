import asyncio
import sys
from datetime import date

from loop import run_agent
from equiflow_data_server import (
    handle_get_order,
    handle_list_orders,
    handle_get_compliance_result,
)

# ---------------------------------------------------------------------------
# EquiFlow wiring
# ---------------------------------------------------------------------------

SYSTEM_TEMPLATE = """
You are an EquiFlow compliance monitoring agent. Today's date is {today}.

Your goal: summarise all compliance breaches for the requested time period
so a compliance officer can review them in one read.

Steps:
1. Call list_orders with status=REJECTED and an appropriate date range based on the question.
2. For each rejected order, call get_compliance_result to retrieve the violation type and reason.
3. Summarise the findings.

Your final response must include:
- Total breach count
- Breakdown by violation type (WASH_SALE vs INSUFFICIENT_FUNDS)
- Which accounts appear more than once (repeat offenders)
- The most recent breach per account

Do not include order IDs unless the user asks for them.
Do not speculate on why a breach occurred beyond what the data shows.
If there are no breaches for the period, say so clearly.
"""

TOOLS = [
    {
        "name": "list_orders",
        "description": (
            "List orders from the EquiFlow database with optional filtering. "
            "Returns paginated results sorted by createdAt descending. "
            "Use this first to find all REJECTED orders for the requested time period."
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
        "name": "get_order",
        "description": (
            "Get a single EquiFlow order by UUID. "
            "Returns current status, order type, ticker, quantity, and user ID. "
            "Use this to retrieve the userId for a rejected order before calling get_compliance_result."
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
        "name": "get_compliance_result",
        "description": (
            "Get the compliance check result for a specific order by UUID. "
            "Returns the overall result (APPROVED or REJECTED), all violations with their type and reason, and the check timestamp. "
            "Use after list_orders for each REJECTED order to retrieve the specific violation type and failure reason. "
            "Only call for REJECTED orders — approved orders have no meaningful violations."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order to retrieve the compliance result for"}
            },
            "required": ["order_id"],
        },
    },
]

DISPATCH = {
    "list_orders":            handle_list_orders,
    "get_order":              handle_get_order,
    "get_compliance_result":  handle_get_compliance_result,
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
