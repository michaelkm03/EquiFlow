import asyncio
import json
import re
import sys
from datetime import date
from pathlib import Path

from loop import run_agent
from equiflow_data_server import handle_list_orders

SYSTEM_TEMPLATE = """
You are an EquiFlow duplicate order detection agent. Today is {today}.

Duplicate: two or more orders with identical (userId, ticker, side, quantity, limitPrice, type) but different IDs.

Suspicion by gap between earliest and latest order in a group:
- HIGH:   < 5 s
- MEDIUM: 5-30 s
- LOW:    > 30 s

Steps:
1. Call list_orders for the date range in the question (default today). Use size=100; if last=false, paginate with page=1, 2... until last=true.
2. Group by (userId, ticker, side, quantity, limitPrice, type).
3. For each group with >1 order, compute gap and assign suspicion.

End your reply with this block (valid JSON, tags unchanged):

<findings_json>
{{
  "verdict": "<CLEAR|REVIEW|ESCALATE>",
  "total_orders": <int>,
  "pairs": [
    {{"orig_id":"<UUID>","dup_id":"<UUID>","user_id":"<userId>","ticker":"<t>","side":"<BUY|SELL>","qty":"<quantity>","price":"<limitPrice>","gap_s":<float>,"suspicion":"<HIGH|MEDIUM|LOW>"}}
  ]
}}
</findings_json>

Verdict: ESCALATE if any HIGH, REVIEW if MEDIUM/LOW only, CLEAR if none. pairs=[] if no duplicates.
"""

TOOLS = [
    {
        "name": "list_orders",
        "description": "List all orders paginated. Use size=100; check last field to paginate.",
        "input_schema": {
            "type": "object",
            "properties": {
                "from": {"type": "string", "description": "Start date YYYY-MM-DD"},
                "to":   {"type": "string", "description": "End date YYYY-MM-DD"},
                "page": {"type": "integer", "description": "0-based page number"},
                "size": {"type": "integer", "description": "Page size (use 100)"},
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
