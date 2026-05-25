"""
Order triage planner — real DB, no LLM.
Fetches order → saga → audit log and applies deterministic rules to produce
a root-cause summary and concrete recommendation.
"""
import json
from typing import AsyncGenerator, Callable

from playbooks.base import extract_uuid


async def run(question: str, call_tool: Callable) -> AsyncGenerator[dict, None]:
    order_id = extract_uuid(question)

    yield {"type": "iteration_start", "iteration": 1}

    # If no UUID in question, find the most recent FAILED order
    if not order_id:
        args = {"status": "FAILED", "size": 1}
        yield {"type": "tool_call", "name": "list_orders", "input": args}
        result = await call_tool("list_orders", args)
        yield {"type": "tool_result", "name": "list_orders", "result": result}
        try:
            data = json.loads(result)
            orders = data.get("content", [])
            if not orders:
                yield {"type": "done", "answer": "No FAILED orders found."}
                return
            order_id = orders[0]["id"]
        except Exception:
            yield {"type": "error", "message": "Failed to retrieve order list."}
            return

    # Fetch order details
    args = {"order_id": order_id}
    yield {"type": "tool_call", "name": "get_order", "input": args}
    result = await call_tool("get_order", args)
    yield {"type": "tool_result", "name": "get_order", "result": result}

    try:
        order = json.loads(result)
    except Exception:
        yield {"type": "error", "message": "Failed to parse order response."}
        return

    saga_id     = order.get("sagaId")
    ticker      = order.get("ticker", "?")
    status      = order.get("status", "?")
    user_id     = order.get("userId", "?")

    # Step 2: Get saga trace
    yield {"type": "iteration_start", "iteration": 2}

    saga          = None
    saga_status   = ""
    failed_step   = None
    failure_reason = None
    retry_count   = 0

    if saga_id:
        args = {"saga_id": saga_id}
        yield {"type": "tool_call", "name": "get_saga", "input": args}
        result = await call_tool("get_saga", args)
        yield {"type": "tool_result", "name": "get_saga", "result": result}
        try:
            saga = json.loads(result)
            saga_status = saga.get("status", "")
            for step in saga.get("steps", []):
                if step.get("status") in ("FAILED", "COMPENSATING"):
                    failed_step    = step.get("name") or step.get("stepName", "unknown")
                    failure_reason = step.get("failureReason") or step.get("failure_reason")
                    retry_count    = step.get("retryCount") or step.get("retry_count", 0)
                    break
        except Exception:
            pass

    # Step 3: Get audit log
    yield {"type": "iteration_start", "iteration": 3}

    args = {"order_id": order_id}
    yield {"type": "tool_call", "name": "query_audit_log", "input": args}
    result = await call_tool("query_audit_log", args)
    yield {"type": "tool_result", "name": "query_audit_log", "result": result}

    audit_retry_count = 0
    try:
        audit = json.loads(result)
        events = audit if isinstance(audit, list) else audit.get("content", [])
        audit_retry_count = sum(1 for e in events if "RETRY" in str(e.get("event", "")).upper())
    except Exception:
        pass

    effective_retries = max(retry_count, audit_retry_count)
    reason_upper = (failure_reason or "").upper()

    # Apply decision rules
    if saga_status == "COMPENSATING":
        recommendation = "ESCALATE — saga is in COMPENSATING state; partial execution was rolled back. Manual intervention required."
    elif any(t in reason_upper for t in ("TIMEOUT", "NETWORK", "CONNECTION")):
        if effective_retries < 3:
            recommendation = f"RETRY — transient failure ({failure_reason}). Only {effective_retries} retries so far, within the auto-retry threshold."
        else:
            recommendation = f"ESCALATE — transient failure ({failure_reason}) but retry limit reached ({effective_retries} attempts)."
    elif any(t in reason_upper for t in ("COMPLIANCE", "REJECTED", "VIOLATION")):
        recommendation = "NO ACTION — order rejected by compliance. Retrying will not succeed."
    elif "INSUFFICIENT" in reason_upper:
        recommendation = "INVESTIGATE — insufficient funds flagged. Verify current ledger balance for this account before deciding."
    else:
        recommendation = "INVESTIGATE — unrecognised failure pattern. Manual review required."

    answer = "\n".join([
        f"Order:      {order_id}",
        f"Ticker:     {ticker}  |  Status: {status}  |  User: {user_id}",
        f"Failed step: {failed_step or 'unknown'}",
        f"Failure reason: {failure_reason or 'unknown'}",
        f"Retries:    {effective_retries}",
        f"Recommendation: {recommendation}",
    ])

    yield {"type": "done", "answer": answer}
