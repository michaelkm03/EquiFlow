"""
Escalation planner — real DB, no LLM.
Fetches order → saga → audit log for each FAILED order in the window,
applies deterministic decision rules, and optionally calls get_ledger_account
for INSUFFICIENT_FUNDS cases. Emits a <findings_json> block.
"""
import json
from datetime import datetime as _dt
from typing import AsyncGenerator, Callable

from playbooks.base import extract_uuid, parse_date_range


_TRANSIENT = {"NETWORK_ERROR", "ORDER_MATCHING_FAILED"}
_NO_ACTION  = {"COMPLIANCE_REJECTED"}
_RETRY_LIMIT = 3


async def _investigate_order(order_id: str, call_tool: Callable) -> dict:
    """Run the full decision flow for a single order. Returns a verdict dict."""

    # Step A: get_order
    result = await call_tool("get_order", {"order_id": order_id})
    try:
        order = json.loads(result)
    except Exception:
        return {"order_id": order_id, "error": f"Could not parse order: {result}"}

    saga_id     = order.get("sagaId")
    user_id     = order.get("userId", "unknown")
    ticker      = order.get("ticker", "?")
    quantity    = order.get("quantity", 0) or 0
    limit_price = order.get("limitPrice", 0) or 0

    saga_status    = ""
    failure_reason = ""
    failed_step    = "unknown"

    # Step B: get_saga
    if saga_id:
        result = await call_tool("get_saga", {"saga_id": saga_id})
        try:
            saga = json.loads(result)
            saga_status    = saga.get("status", "")
            failure_reason = saga.get("failureReason", "")
            for step in saga.get("steps", []):
                if step.get("status") in ("FAILED", "COMPENSATING"):
                    failed_step = step.get("stepName", "unknown")
                    break
        except Exception:
            pass

    # Step C: query_audit_log — always; derive retry_count
    result = await call_tool("query_audit_log", {"order_id": order_id})
    retry_count = 0
    try:
        events = json.loads(result)
        if isinstance(events, dict):
            events = events.get("content", [])
        retry_count = sum(1 for e in events if "RETRY" in str(e.get("event", "")).upper())
    except Exception:
        pass

    # Decision rules
    action      = "INVESTIGATE"
    incident_id = None
    explanation = "Unrecognised failure pattern; manual review required."

    if saga_status == "COMPENSATING":
        inc_result = await call_tool("create_incident", {
            "order_id": order_id,
            "severity": "CRITICAL",
            "reason": "Saga compensation — manual financial reconciliation required",
        })
        try:
            incident_id = json.loads(inc_result).get("incident_id")
        except Exception:
            pass
        action      = "ESCALATE"
        explanation = "Saga is in COMPENSATING state; financial reconciliation required."

    elif failure_reason in _TRANSIENT:
        if retry_count < _RETRY_LIMIT:
            action      = "RETRY"
            explanation = f"Transient failure ({failure_reason}); {retry_count} retries so far, within threshold."
        else:
            inc_result = await call_tool("create_incident", {
                "order_id": order_id,
                "severity": "HIGH",
                "reason": f"Retry limit reached after {retry_count} attempts ({failure_reason})",
            })
            try:
                incident_id = json.loads(inc_result).get("incident_id")
            except Exception:
                pass
            action      = "ESCALATE"
            explanation = f"Retry limit reached ({retry_count} attempts); escalating."

    elif failure_reason in _NO_ACTION:
        action      = "NO_ACTION"
        explanation = "Order rejected by compliance; retrying will not succeed."

    elif failure_reason == "INSUFFICIENT_FUNDS":
        ledger_result = await call_tool("get_ledger_account", {"user_id": user_id})
        available_cash = 0.0
        try:
            ledger = json.loads(ledger_result)
            available_cash = float(ledger.get("availableCash", 0) or 0)
        except Exception:
            pass

        required = float(quantity) * float(limit_price)

        if available_cash >= required:
            action      = "INVESTIGATE"
            explanation = (
                f"Balance recovered (available: {available_cash:.2f}, "
                f"required: {required:.2f}); manual settlement retry possible."
            )
        else:
            inc_result = await call_tool("create_incident", {
                "order_id": order_id,
                "severity": "HIGH",
                "reason": f"Account balance still insufficient (available: {available_cash:.2f}, required: {required:.2f})",
            })
            try:
                incident_id = json.loads(inc_result).get("incident_id")
            except Exception:
                pass
            action      = "ESCALATE"
            explanation = f"Balance still insufficient (available: {available_cash:.2f}, required: {required:.2f})."

    return {
        "order_id":       order_id,
        "user_id":        user_id,
        "ticker":         ticker,
        "failed_step":    failed_step,
        "failure_reason": failure_reason or "unknown",
        "saga_status":    saga_status or "unknown",
        "retry_count":    retry_count,
        "action":         action,
        "incident_id":    incident_id,
        "explanation":    explanation,
    }


async def run(question: str, call_tool: Callable) -> AsyncGenerator[dict, None]:
    order_id = extract_uuid(question)
    from_date, to_date = parse_date_range(question)

    # Triggered mode: single order UUID in question
    if order_id:
        yield {"type": "iteration_start", "iteration": 1}
        yield {"type": "tool_call", "name": "get_order", "input": {"order_id": order_id}}
        verdict = await _investigate_order(order_id, call_tool)
        verdicts = [verdict]
        mode = "triggered"
    else:
        # Scan mode: find all FAILED orders in window
        yield {"type": "iteration_start", "iteration": 1}
        all_orders: list[dict] = []
        page = 0
        while True:
            args = {"status": "FAILED", "from": from_date, "to": to_date, "page": page, "size": 100}
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

        verdicts = []
        for i, order in enumerate(all_orders):
            yield {"type": "iteration_start", "iteration": i + 2}
            oid = order.get("id")
            verdict = await _investigate_order(oid, call_tool)
            verdicts.append(verdict)

        mode = "scan"

    overall = "ESCALATE" if any(v.get("action") == "ESCALATE" for v in verdicts) else "ALL_CLEAR"

    findings = {
        "mode": mode,
        "window": {"from": from_date, "to": to_date},
        "total_investigated": len(verdicts),
        "verdicts": verdicts,
        "verdict": overall,
    }

    lines = []
    for v in verdicts:
        lines.append(
            f"Order {v['order_id']} | {v['ticker']} | {v['failure_reason']} "
            f"| retries: {v['retry_count']} | → {v['action']}"
        )
        if v.get("incident_id"):
            lines.append(f"  Incident: {v['incident_id']}")

    summary = "\n".join(lines) if lines else "No FAILED orders found in window."
    answer = (
        f"{summary}\n\nOverall verdict: {overall}\n\n"
        f"<findings_json>\n{json.dumps(findings, indent=2)}\n</findings_json>"
    )

    yield {"type": "done", "answer": answer}
