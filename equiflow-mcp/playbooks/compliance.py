"""
Compliance planner — real DB, no LLM.
Lists all REJECTED orders for the period, fetches each compliance result,
groups by violation type and userId, identifies repeat offenders.
"""
import json
from collections import defaultdict
from typing import AsyncGenerator, Callable

from playbooks.base import parse_date_range


async def run(question: str, call_tool: Callable) -> AsyncGenerator[dict, None]:
    from_date, to_date = parse_date_range(question)

    # Step 1: Fetch all REJECTED orders for the period
    yield {"type": "iteration_start", "iteration": 1}
    all_orders: list[dict] = []
    page = 0
    while True:
        args = {"status": "REJECTED", "from": from_date, "to": to_date, "page": page, "size": 100}
        yield {"type": "tool_call", "name": "list_orders", "input": args}
        result = await call_tool("list_orders", args)
        yield {"type": "tool_result", "name": "list_orders", "result": result}
        try:
            data = json.loads(result)
            all_orders.extend(data.get("content", []))
            if data.get("last", True):
                break
            page += 1
        except Exception:
            break

    if not all_orders:
        findings = {
            "period": {"from": from_date, "to": to_date},
            "total_breaches": 0,
            "violation_breakdown": {"WASH_SALE_VIOLATION": 0, "INSUFFICIENT_FUNDS": 0},
            "repeat_offenders": [],
            "verdict": "CLEAR",
        }
        yield {"type": "done", "answer": (
            f"No compliance breaches found ({from_date} to {to_date}).\n\n"
            f"<findings_json>\n{json.dumps(findings, indent=2)}\n</findings_json>"
        )}
        return

    # Step 2: Fetch compliance result for each rejected order
    yield {"type": "iteration_start", "iteration": 2}

    # user_id -> list of {type, date}
    user_violations: dict[str, list] = defaultdict(list)
    type_counts: dict[str, int] = defaultdict(int)

    for order in all_orders:
        order_id = order.get("id")
        user_id = order.get("userId", "unknown")
        created_at = (order.get("createdAt") or "")[:10]

        args = {"order_id": order_id}
        yield {"type": "tool_call", "name": "get_compliance_result", "input": args}
        result = await call_tool("get_compliance_result", args)
        yield {"type": "tool_result", "name": "get_compliance_result", "result": result}

        try:
            cr = json.loads(result)
            for v in cr.get("violations", []):
                raw = v.get("type") or v.get("code", "UNKNOWN")
                # Normalise API code to canonical violation type
                if raw in ("WASH_SALE", "WASH_SALE_VIOLATION"):
                    vtype = "WASH_SALE_VIOLATION"
                elif raw in ("INSUFFICIENT_FUNDS",):
                    vtype = "INSUFFICIENT_FUNDS"
                else:
                    vtype = raw
                type_counts[vtype] += 1
                user_violations[user_id].append({"type": vtype, "date": created_at})
        except Exception:
            pass

    # Step 3: Compute findings
    repeat_offenders = []
    for user_id, viols in user_violations.items():
        if len(viols) > 1:
            latest = max(v["date"] for v in viols)
            types = sorted({v["type"] for v in viols})
            repeat_offenders.append({
                "user_id": user_id,
                "breach_count": len(viols),
                "latest_breach": latest,
                "types": types,
            })

    total = sum(type_counts.values())
    verdict = "ESCALATE" if repeat_offenders else ("REVIEW" if total > 0 else "CLEAR")

    findings = {
        "period": {"from": from_date, "to": to_date},
        "total_breaches": total,
        "violation_breakdown": {
            "WASH_SALE_VIOLATION": type_counts.get("WASH_SALE_VIOLATION", 0),
            "INSUFFICIENT_FUNDS":  type_counts.get("INSUFFICIENT_FUNDS", 0),
        },
        "repeat_offenders": repeat_offenders,
        "verdict": verdict,
    }

    summary_lines = [f"{total} compliance breach{'es' if total != 1 else ''} found ({from_date} to {to_date})."]
    if repeat_offenders:
        summary_lines.append(f"{len(repeat_offenders)} repeat offender(s). Verdict: ESCALATE.")
    elif total > 0:
        summary_lines.append("No repeat offenders. Verdict: REVIEW.")
    else:
        summary_lines.append("Verdict: CLEAR.")

    yield {"type": "done", "answer": (
        "\n".join(summary_lines) + f"\n\n<findings_json>\n{json.dumps(findings, indent=2)}\n</findings_json>"
    )}
