"""
Duplicate detection planner — real DB, no LLM.
Groups orders by (userId, ticker, side, quantity, limitPrice, type) and
classifies each pair by time gap: HIGH < 1s, MEDIUM 1-5s, LOW > 5s.
"""
import json
from collections import defaultdict
from datetime import datetime as _dt
from typing import AsyncGenerator, Callable

from playbooks.base import parse_date_range


async def run(question: str, call_tool: Callable) -> AsyncGenerator[dict, None]:
    from_date, to_date = parse_date_range(question)
    call_input = {"from": from_date, "to": to_date, "size": 100}

    yield {"type": "iteration_start", "iteration": 1}
    yield {"type": "tool_call", "name": "list_orders", "input": call_input}

    all_orders: list[dict] = []
    page = 0
    while True:
        result = await call_tool("list_orders", {**call_input, "page": page})
        yield {"type": "tool_result", "name": "list_orders", "result": result}
        try:
            data = json.loads(result)
            all_orders.extend(data.get("content", []))
            if data.get("last", True):
                break
            page += 1
            yield {"type": "tool_call", "name": "list_orders", "input": {**call_input, "page": page}}
        except Exception:
            break

    yield {"type": "iteration_start", "iteration": 2}

    groups: dict[tuple, list] = defaultdict(list)
    for o in all_orders:
        key = (
            o.get("userId", ""), o.get("ticker", ""), o.get("side", ""),
            str(o.get("quantity", "")), str(o.get("limitPrice", "")), o.get("type", ""),
        )
        groups[key].append(o)

    pairs = []
    for orders in groups.values():
        if len(orders) < 2:
            continue
        orders.sort(key=lambda o: o.get("createdAt", ""))
        for i in range(len(orders) - 1):
            orig, dup = orders[i], orders[i + 1]
            try:
                gap_s = round(
                    (_dt.fromisoformat(dup["createdAt"]) - _dt.fromisoformat(orig["createdAt"])).total_seconds(),
                    2,
                )
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
    answer = (
        f"Scanned {len(all_orders)} orders ({from_date} to {to_date}). {summary}.\n\n"
        f"<findings_json>\n{json.dumps(findings, separators=(',', ':'))}\n</findings_json>"
    )
    yield {"type": "done", "answer": answer}
