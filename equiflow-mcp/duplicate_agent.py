import asyncio
import json
import re
import sys
from datetime import date
from pathlib import Path

from loop import run_agent
from equiflow_data_server import handle_list_orders

SYSTEM_TEMPLATE = """
You are an EquiFlow duplicate order detection agent. Today's date is {today}.

Your goal: identify orders that appear to be duplicates — same user, ticker, side,
quantity, limitPrice, and type — placed within a short time window.

A duplicate is two or more orders with identical fields (userId, ticker, side,
quantity, limitPrice, type) but different order IDs.

Suspicion levels based on time between the first and last order in a duplicate group:
- HIGH:   < 5 seconds apart   — almost certainly accidental (double-click, client retry)
- MEDIUM: 5–30 seconds apart  — possible retry or API glitch
- LOW:    > 30 seconds apart  — may be intentional; flag for human review

Steps:
1. Call list_orders with the date range from the question (default to today).
   Use size=100. If the response shows more pages exist, call list_orders again
   with page=1, page=2, etc. until all orders are retrieved.
2. Group orders by (userId, ticker, side, quantity, limitPrice, type).
3. For each group with more than one order, calculate the time delta between
   the earliest and latest order and assign a suspicion level.

Your final response must include:
- Total duplicate pairs found (0 means clean)
- A duplicate pairs table with exactly these columns (pipe-separated, padded for alignment):
    User | Ticker | Side | Qty | Price | Gap | Suspicion | Original UUID | Duplicate UUID
  One row per duplicate pair. Always include both UUIDs so results can be cross-referenced with the seed script output.
- Which users appear in more than one duplicate group (repeat offenders)
- Overall assessment:
    CLEAR    — no duplicates found
    REVIEW   — MEDIUM or LOW pairs only
    ESCALATE — at least one HIGH pair (both orders may execute)

Always include order UUIDs in the table — they are required for cross-referencing with the seed script summary.
Do not speculate on intent beyond what the time delta and fields suggest.
If no orders are found for the period, say so clearly.

After your human-readable report, append this machine-readable block exactly as shown
(keep the tag names unchanged; output valid JSON):

<findings_json>
[
  {{
    "orig_id":   "<UUID of the earlier order>",
    "dup_id":    "<UUID of the later order>",
    "user_id":   "<userId field from the order>",
    "ticker":    "<ticker>",
    "side":      "<BUY or SELL>",
    "qty":       "<quantity as a plain number, e.g. 25>",
    "price":     "<limitPrice as a plain decimal, e.g. 588.31>",
    "gap_s":     <float seconds between the two orders>,
    "suspicion": "<HIGH, MEDIUM, or LOW>"
  }}
]
</findings_json>

One object per duplicate pair. Output [] if no duplicates were found.
"""

TOOLS = [
    {
        "name": "list_orders",
        "description": (
            "List orders from the EquiFlow database with optional filtering. "
            "Supports filtering by userId, status, ticker, and date range. "
            "Returns paginated results sorted by createdAt descending. "
            "Use size=100 and the page param to retrieve large result sets."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "userId": {
                    "type": "string",
                    "description": "Filter by user UUID",
                },
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
                "from":   {"type": "string", "description": "Start date YYYY-MM-DD"},
                "to":     {"type": "string", "description": "End date YYYY-MM-DD"},
                "page":   {"type": "integer", "description": "Page number (0-based, default 0)"},
                "size":   {"type": "integer", "description": "Page size (default 25, use 100 for bulk scans)"},
            },
            "required": [],
        },
    },
]

DISPATCH = {"list_orders": handle_list_orders}


async def call_tool(name: str, args: dict) -> str:
    handler = DISPATCH.get(name)
    if handler is None:
        return f"Unknown tool: {name}"
    results = await handler(args)
    return results[0].text if results else ""


def _extract_findings(answer: str) -> list[dict]:
    m = re.search(r"<findings_json>\s*(.*?)\s*</findings_json>", answer, re.DOTALL)
    if not m:
        return []
    try:
        return json.loads(m.group(1))
    except json.JSONDecodeError:
        return []


def _strip_findings_block(answer: str) -> str:
    return re.sub(r"\n*<findings_json>.*?</findings_json>", "", answer, flags=re.DOTALL).rstrip()


async def main():
    system   = SYSTEM_TEMPLATE.format(today=date.today())
    question = " ".join(sys.argv[1:]) if len(sys.argv) > 1 else input("Question: ")
    print()
    answer   = await run_agent(system, TOOLS, call_tool, question)

    findings = _extract_findings(answer)
    if findings:
        findings_file = Path(__file__).parent / "agent_findings.json"
        findings_file.write_text(json.dumps({"date": str(date.today()), "pairs": findings}, indent=2))
        print(f"\n[saved {len(findings)} findings → {findings_file.name}]")

    print(f"\n{_strip_findings_block(answer)}")


if __name__ == "__main__":
    asyncio.run(main())
