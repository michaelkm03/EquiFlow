"""
Escalation planner — real DB, no LLM.
5-phase pipeline per order:
  Phase 1  Triage:            get_order → get_saga → query_audit_log
  Phase 2  Pattern Analysis:  list_recent_failures → systemic_risk flag
  Phase 3  Enrichment:        get_service_health (transient) | get_user_risk_profile + get_ledger_account (INSUF_FUNDS)
  Phase 4  Confidence Guard:  downgrade to INVESTIGATE if confidence < 0.6
  Phase 5  Verdict:           emit findings_json with confidence, priority, evidence[], recommended_actions[]
"""
import json
from typing import AsyncGenerator, Callable

from playbooks.base import extract_uuid, parse_date_range

# Failures caused by temporary infrastructure issues — order itself is valid, safe to retry
_TRANSIENT    = {"NETWORK_ERROR", "ORDER_MATCHING_FAILED"}

# Failures where retrying will never succeed — policy decision, not a system error
_NO_ACTION    = {"COMPLIANCE_REJECTED"}

# Max manual re-submissions before escalating a transient failure
_RETRY_LIMIT  = 3

# Maps failure codes to the downstream service responsible, used in Phase 3 health check
_FAILURE_SERVICE_MAP = {
    "NETWORK_ERROR":         "matching-service",
    "ORDER_MATCHING_FAILED": "matching-service",
    "LEDGER_DEBIT_FAILED":   "ledger-service",
    "SETTLEMENT_FAILED":     "settlement-service",
}


async def _investigate_order(order_id: str, call_tool: Callable) -> dict:
    evidence:    list[str] = []
    # Confidence starts high; decremented when data is missing or unparseable.
    # Phase 3 overwrites it with a branch-specific fixed value.
    # Phase 4 guards the final verdict if it falls below 0.6.
    confidence:  float     = 0.9

    # ── Phase 1: Triage ───────────────────────────────────────────────────────
    # Goal: collect the core facts — what failed, where in the saga, and how many
    # times it has been retried. These fields drive every downstream decision.

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

    if saga_id:
        result = await call_tool("get_saga", {"saga_id": saga_id})
        try:
            saga           = json.loads(result)
            saga_status    = saga.get("status", "")
            # failureReason is the enum code that drives Phase 3 branching
            failure_reason = saga.get("failureReason", "")
            # Find the first step that broke so we know exactly where the saga stopped
            for step in saga.get("steps", []):
                if step.get("status") in ("FAILED", "COMPENSATING"):
                    failed_step = step.get("stepName", "unknown")
                    break
        except Exception:
            evidence.append("Could not parse saga response")
            confidence -= 0.2
    else:
        evidence.append("No saga ID on order")
        confidence -= 0.1

    result = await call_tool("query_audit_log", {"order_id": order_id})
    retry_count = 0
    try:
        events = json.loads(result)
        if isinstance(events, dict):
            events = events.get("content", [])
        # retry_count reflects manual re-submissions; no auto-retry exists in this system
        retry_count = sum(1 for e in events if "RETRY" in str(e.get("event", "")).upper())
    except Exception:
        evidence.append("Could not parse audit log")
        confidence -= 0.1

    # ── Phase 2: Pattern Analysis ─────────────────────────────────────────────
    # Goal: detect whether this is an isolated failure or part of a broader outage.
    # Only meaningful for infrastructure failures (_TRANSIENT + LEDGER_DEBIT_FAILED).
    # Policy failures (COMPLIANCE_REJECTED, INSUFFICIENT_FUNDS) are never systemic —
    # they reflect individual account/rule state, not a platform-wide problem.
    # list_recent_failures counts ALL FAILED orders regardless of failure_reason,
    # so we gate the check here to avoid false positives on unrelated failure types.

    _SYSTEMIC_ELIGIBLE = _TRANSIENT | {"LEDGER_DEBIT_FAILED", "SETTLEMENT_FAILED"}

    systemic_risk = False
    recent_count  = 0
    if failure_reason in _SYSTEMIC_ELIGIBLE or not failure_reason:
        recent_result = await call_tool("list_recent_failures", {
            "failure_reason": failure_reason,
            "minutes": 15,
        })
        try:
            recent        = json.loads(recent_result)
            recent_count  = recent.get("count", 0)
            # is_systemic is true when count >= 3 (threshold set in equiflow_data_server)
            systemic_risk = recent.get("is_systemic", False)
            if systemic_risk:
                evidence.append(f"Systemic pattern: {recent_count} FAILED orders in last 15 minutes")
                confidence = min(confidence, 0.85)
        except Exception:
            evidence.append("Could not assess systemic risk")

    # ── Phase 3: Decision + Context Enrichment ────────────────────────────────
    # Goal: assign a verdict and priority by branching on failure_reason.
    # Each branch fetches only the context it needs to make an accurate decision.
    # Default to INVESTIGATE so unrecognised failures always get human review.

    verdict     = "INVESTIGATE"
    priority    = "MEDIUM"
    incident_id = None
    explanation = "Unrecognised failure pattern; manual review required."

    if saga_status == "COMPENSATING":
        # COMPENSATING always takes highest precedence — money is already in-flight,
        # financial reconciliation is required regardless of any systemic pattern.
        # systemic_risk is noted in evidence but does not downgrade this to FLAG_SYSTEMIC.
        inc = await call_tool("create_incident", {
            "order_id": order_id,
            "severity": "CRITICAL",
            "reason":   "Saga compensation — manual financial reconciliation required",
        })
        try:
            incident_id = json.loads(inc).get("incident_id")
        except Exception:
            pass
        verdict     = "ESCALATE"
        priority    = "CRITICAL"
        confidence  = 0.95
        evidence.append("Saga in COMPENSATING state — rollback in progress")
        if systemic_risk:
            evidence.append(f"Also part of systemic pattern: {recent_count} FAILED orders in last 15 minutes")
        explanation = "Saga is in COMPENSATING state; financial reconciliation required."

    elif systemic_risk:
        # Multiple orders failing in the same window — treat as a platform incident
        inc = await call_tool("create_incident", {
            "order_id": order_id,
            "severity": "HIGH",
            "reason":   f"Systemic: {recent_count} orders failed with {failure_reason or 'unknown'} in last 15 min",
        })
        try:
            incident_id = json.loads(inc).get("incident_id")
        except Exception:
            pass
        verdict     = "FLAG_SYSTEMIC"
        priority    = "HIGH"
        explanation = f"Pattern detected: {recent_count} recent failures share this failure type."

    elif failure_reason in _TRANSIENT:
        # Infrastructure blip — check whether the responsible service is healthy
        # before deciding to retry or escalate
        service = _FAILURE_SERVICE_MAP.get(failure_reason, "unknown-service")
        health  = await call_tool("get_service_health", {"service": service})
        service_status = "HEALTHY"
        try:
            service_status = json.loads(health).get("status", "HEALTHY")
            evidence.append(f"Service health ({service}): {service_status}")
        except Exception:
            evidence.append(f"Could not check service health for {service}")

        if service_status in ("DEGRADED", "DOWN"):
            # Service-level outage — retrying individual orders won't help
            inc = await call_tool("create_incident", {
                "order_id": order_id,
                "severity": "HIGH",
                "reason":   f"Service {service} is {service_status}",
            })
            try:
                incident_id = json.loads(inc).get("incident_id")
            except Exception:
                pass
            verdict     = "FLAG_SYSTEMIC"
            priority    = "HIGH"
            confidence  = 0.90
            explanation = f"{service} is {service_status}; this is a service-level failure."
        elif retry_count < _RETRY_LIMIT:
            # Service is healthy and we haven't exhausted retries — safe to re-submit
            verdict     = "RETRY"
            priority    = "LOW"
            confidence  = 0.90
            evidence.append(f"Transient failure, retry_count={retry_count} < limit={_RETRY_LIMIT}")
            explanation = f"Transient failure ({failure_reason}); {retry_count} retries so far, within threshold."
        else:
            # Retry limit exhausted on a healthy service — something else is wrong
            inc = await call_tool("create_incident", {
                "order_id": order_id,
                "severity": "HIGH",
                "reason":   f"Retry limit reached after {retry_count} attempts ({failure_reason})",
            })
            try:
                incident_id = json.loads(inc).get("incident_id")
            except Exception:
                pass
            verdict     = "ESCALATE"
            priority    = "HIGH"
            confidence  = 0.90
            evidence.append(f"Retry limit exceeded: {retry_count} >= {_RETRY_LIMIT}")
            explanation = f"Retry limit reached ({retry_count} attempts); escalating."

    elif failure_reason in _NO_ACTION:
        # Compliance rejection is a policy decision — no system action will change the outcome
        verdict     = "NO_ACTION"
        priority    = "NONE"
        confidence  = 0.95
        evidence.append("Compliance rejection — policy-based, retry will not succeed")
        explanation = "Order rejected by compliance; retrying will not succeed."

    elif failure_reason == "INSUFFICIENT_FUNDS":
        # Two sub-paths: chronic pattern (account review needed) vs one-time shortfall.
        # Also check if balance has since recovered — if so, a manual retry may succeed.
        risk_result = await call_tool("get_user_risk_profile", {"user_id": user_id})
        total_failed_30d = 0
        try:
            risk             = json.loads(risk_result)
            total_failed_30d = risk.get("total_failed_30d", 0)
            risk_level       = risk.get("risk_level", "LOW")
            evidence.append(f"User risk: {total_failed_30d} failures in 30 days (level={risk_level})")
        except Exception:
            evidence.append("Could not retrieve user risk profile")
            confidence -= 0.1

        ledger_result  = await call_tool("get_ledger_account", {"user_id": user_id})
        available_cash = 0.0
        try:
            available_cash = float(json.loads(ledger_result).get("availableCash", 0) or 0)
        except Exception:
            evidence.append("Could not retrieve ledger balance")
            confidence -= 0.1

        # required = cost of the order at the time it was placed
        required = float(quantity) * float(limit_price)

        if total_failed_30d >= 5:
            # Repeated failures suggest a behavioural pattern — flag for account review
            inc = await call_tool("create_incident", {
                "order_id": order_id,
                "severity": "MEDIUM",
                "reason":   f"Chronic failures: user has {total_failed_30d} FAILED orders in last 30 days",
            })
            try:
                incident_id = json.loads(inc).get("incident_id")
            except Exception:
                pass
            verdict     = "ESCALATE"
            priority    = "MEDIUM"
            confidence  = 0.80
            explanation = f"Chronic pattern: {total_failed_30d} failures in 30 days; account review required."
        elif available_cash >= required:
            # Balance has recovered since the failure — operator can retry manually
            verdict     = "INVESTIGATE"
            priority    = "LOW"
            confidence  = 0.80
            evidence.append(f"Balance recovered: available={available_cash:.2f}, required={required:.2f}")
            explanation = f"Balance recovered (available: {available_cash:.2f}, required: {required:.2f}); manual retry possible."
        else:
            # Still insufficient — notify account holder, no system action needed
            verdict     = "NO_ACTION"
            priority    = "LOW"
            confidence  = 0.85
            evidence.append(f"One-time shortfall: available={available_cash:.2f}, required={required:.2f}")
            explanation = f"One-time shortfall (available: {available_cash:.2f}, required: {required:.2f}); notify account holder."

    # ── Phase 4: Confidence Guard ─────────────────────────────────────────────
    # Goal: prevent a low-signal verdict from being acted on automatically.
    # Triggers when Phase 1 parse failures have accumulated enough penalty (< 0.6)
    # and Phase 3 didn't overwrite confidence with a branch-specific value.

    if confidence < 0.6:
        evidence.append(f"Confidence {confidence:.2f} below threshold — downgrading verdict to INVESTIGATE")
        verdict  = "INVESTIGATE"
        priority = "MEDIUM"

    # ── Phase 5: Build recommended_actions ────────────────────────────────────
    # Goal: translate the verdict into concrete next steps for the operator.
    # Appended after all phases so the list always reflects the final verdict,
    # including any Phase 4 downgrade.

    recommended_actions: list[str] = []
    if incident_id:
        recommended_actions.append(f"Incident created: {incident_id}")
    if verdict == "RETRY":
        recommended_actions.append("Re-submit order via saga orchestrator")
    elif verdict == "NO_ACTION":
        recommended_actions.append("No system action required")
    elif verdict == "INVESTIGATE":
        recommended_actions.append("Manual review required")
    elif verdict == "FLAG_SYSTEMIC":
        recommended_actions.append("Notify ops team — pattern affects multiple orders")

    return {
        "order_id":            order_id,
        "user_id":             user_id,
        "ticker":              ticker,
        "failed_step":         failed_step,
        "failure_reason":      failure_reason or "unknown",
        "saga_status":         saga_status or "unknown",
        "retry_count":         retry_count,
        "systemic_risk":       systemic_risk,
        "verdict":             verdict,
        "priority":            priority,
        "confidence":          round(confidence, 2),
        "evidence":            evidence,
        "recommended_actions": recommended_actions,
        "incident_id":         incident_id,
        "explanation":         explanation,
    }


async def run(question: str, call_tool: Callable) -> AsyncGenerator[dict, None]:
    order_id = extract_uuid(question)
    from_date, to_date = parse_date_range(question)

    if order_id:
        # Triggered mode: investigate a single order by UUID
        yield {"type": "iteration_start", "iteration": 1}
        yield {"type": "tool_call", "name": "get_order", "input": {"order_id": order_id}}
        verdict  = await _investigate_order(order_id, call_tool)
        verdicts = [verdict]
        mode     = "triggered"
    else:
        # Scan mode: paginate all FAILED orders in the requested date window
        yield {"type": "iteration_start", "iteration": 1}
        all_orders: list[dict] = []
        page = 0
        while True:
            args = {"status": "FAILED", "from": from_date, "to": to_date, "page": page, "size": 100}
            yield {"type": "tool_call",   "name": "list_orders", "input": args}
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
            verdicts.append(await _investigate_order(order.get("id"), call_tool))
        mode = "scan"

    # Overall verdict hierarchy: FLAG_SYSTEMIC > ESCALATE > ALL_CLEAR
    has_systemic = any(v.get("verdict") == "FLAG_SYSTEMIC" for v in verdicts)
    has_escalate = any(v.get("verdict") == "ESCALATE"     for v in verdicts)
    if has_systemic:
        overall = "FLAG_SYSTEMIC"
    elif has_escalate:
        overall = "ESCALATE"
    else:
        overall = "ALL_CLEAR"

    findings = {
        "mode":               mode,
        "window":             {"from": from_date, "to": to_date},
        "total_investigated": len(verdicts),
        "verdicts":           verdicts,
        "overall_verdict":    overall,
    }

    lines = []
    for v in verdicts:
        lines.append(
            f"Order {v['order_id']} | {v['ticker']} | {v['failure_reason']} "
            f"| retries: {v['retry_count']} | confidence: {v['confidence']} | → {v['verdict']} [{v['priority']}]"
        )
        if v.get("incident_id"):
            lines.append(f"  Incident: {v['incident_id']}")
        for ev in v.get("evidence", []):
            lines.append(f"  Evidence: {ev}")

    summary = "\n".join(lines) if lines else "No FAILED orders found in window."
    answer = (
        f"{summary}\n\nOverall verdict: {overall}\n\n"
        f"<findings_json>\n{json.dumps(findings, indent=2)}\n</findings_json>"
    )
    yield {"type": "done", "answer": answer}
