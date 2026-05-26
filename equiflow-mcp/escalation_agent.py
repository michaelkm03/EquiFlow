"""
Order failure escalation agent.
Diagnoses FAILED orders — identifies root cause, checks retry history and ledger
balance, then recommends RETRY / INVESTIGATE / ESCALATE / NO_ACTION per order.

Invocation modes:
  CLI (Kafka-triggered): python escalation_agent.py <order_id>
  UI (on-demand):        imported by api.py as SYSTEM_TEMPLATE + TOOLS
"""
import asyncio
import sys
from datetime import date

from loop import run_agent
from equiflow_data_server import (
    handle_list_orders,
    handle_get_order,
    handle_get_saga,
    handle_query_audit_log,
    handle_get_ledger_account,
    handle_create_incident,
    handle_list_recent_failures,
    handle_get_service_health,
    handle_get_user_risk_profile,
)

# ---------------------------------------------------------------------------
# Agent wiring
# ---------------------------------------------------------------------------

SYSTEM_TEMPLATE = """
You are an EquiFlow order failure escalation agent. Today is {today}.

You may be invoked in two ways:
1. A specific order ID is provided — start Phase 1 immediately with get_order.
2. A time window question — call list_orders(status=FAILED) to find all failures first.

For every FAILED order, run this 5-phase pipeline:

PHASE 1 — TRIAGE
  get_order(order_id)        → saga_id, user_id, quantity, limitPrice
  get_saga(saga_id)          → failure_reason (enum code), saga.status, failed stepName
  query_audit_log(order_id)  → always call; count RETRY events → retry_count

PHASE 2 — PATTERN ANALYSIS
  Only for infrastructure failures [NETWORK_ERROR, ORDER_MATCHING_FAILED, LEDGER_DEBIT_FAILED, SETTLEMENT_FAILED].
  Skip Phase 2 for COMPLIANCE_REJECTED and INSUFFICIENT_FUNDS — those are never systemic.
  list_recent_failures(failure_reason=<code>, minutes=15)
  If count >= 3: systemic_risk=true

PHASE 3 — CONTEXT ENRICHMENT (branch by failure_reason)
  COMPENSATING saga (always highest precedence — check this before systemic):
    → verdict=ESCALATE, priority=CRITICAL, confidence=0.95
    → create_incident(severity=CRITICAL, reason="Saga compensation — financial reconciliation required")
    → if systemic_risk is also true: note in evidence[] but do not change verdict to FLAG_SYSTEMIC
  Systemic pattern (count >= 3 from Phase 2):
    → verdict=FLAG_SYSTEMIC, priority=HIGH, confidence=0.85
    → create_incident(severity=HIGH)
  Transient [NETWORK_ERROR, ORDER_MATCHING_FAILED]:
    get_service_health(service=<matching-service or ledger-service>)
    DEGRADED/DOWN → FLAG_SYSTEMIC, create_incident(severity=HIGH)
    retry_count < 3 → RETRY, priority=LOW
    retry_count >= 3 → ESCALATE, create_incident(severity=HIGH)
  COMPLIANCE_REJECTED:
    → NO_ACTION, priority=NONE, confidence=0.95
  INSUFFICIENT_FUNDS:
    get_user_risk_profile(user_id)
    get_ledger_account(user_id)
    total_failed_30d >= 5 → ESCALATE, create_incident(severity=MEDIUM)
    availableCash < required → NO_ACTION (one-time shortfall)
    availableCash >= required → INVESTIGATE (balance recovered)
  Unrecognised:
    → INVESTIGATE, priority=MEDIUM, confidence=0.50

PHASE 4 — CONFIDENCE GUARD
  If confidence < 0.6: downgrade verdict to INVESTIGATE

PHASE 5 — VERDICT
Do not speculate beyond what the tools return. Note missing data in evidence[].

End your reply with this block (valid JSON, tags unchanged):

<findings_json>
{{
  "mode": "<triggered|scan>",
  "window": {{"from": "<ISO>", "to": "<ISO>"}},
  "total_investigated": <int>,
  "verdicts": [
    {{
      "order_id": "<UUID>",
      "user_id": "<userId>",
      "ticker": "<ticker>",
      "failed_step": "<stepName or unknown>",
      "failure_reason": "<exact enum code>",
      "saga_status": "<FAILED|COMPENSATING>",
      "retry_count": <int>,
      "systemic_risk": <true|false>,
      "verdict": "<RETRY|NO_ACTION|INVESTIGATE|ESCALATE|FLAG_SYSTEMIC>",
      "priority": "<CRITICAL|HIGH|MEDIUM|LOW|NONE>",
      "confidence": <0.0-1.0>,
      "evidence": ["<one sentence per signal>"],
      "recommended_actions": ["<action taken>"],
      "incident_id": "<PD-XXXX or null>"
    }}
  ],
  "overall_verdict": "<ALL_CLEAR|ESCALATE|FLAG_SYSTEMIC>"
}}
</findings_json>

overall_verdict: FLAG_SYSTEMIC if systemic pattern detected. ESCALATE if any incident created. ALL_CLEAR otherwise.
"""

TOOLS = [
    {
        "name": "list_orders",
        "description": (
            "List orders with optional filtering. Use status=FAILED and a date range "
            "to find all failed orders in the requested window. Paginate with page param."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "status": {"type": "string", "enum": ["FAILED", "PENDING", "OPEN", "REJECTED", "CANCELLED", "FILLED"]},
                "from":   {"type": "string", "description": "Start date YYYY-MM-DD"},
                "to":     {"type": "string", "description": "End date YYYY-MM-DD"},
                "page":   {"type": "integer"},
                "size":   {"type": "integer"},
            },
            "required": [],
        },
    },
    {
        "name": "get_order",
        "description": (
            "Get a single order by UUID. Returns status, sagaId, userId, ticker, "
            "quantity, and limitPrice. Call first when investigating a specific order."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order"},
            },
            "required": ["order_id"],
        },
    },
    {
        "name": "get_saga",
        "description": (
            "Get the saga execution trace by saga UUID. Returns saga.status, "
            "saga.failureReason (enum code), and steps with stepName and errorMessage. "
            "failureReason codes: COMPLIANCE_REJECTED, INSUFFICIENT_FUNDS, "
            "ORDER_MATCHING_FAILED, LEDGER_DEBIT_FAILED, NETWORK_ERROR."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "saga_id": {"type": "string", "description": "UUID of the saga"},
            },
            "required": ["saga_id"],
        },
    },
    {
        "name": "query_audit_log",
        "description": (
            "Get the full audit trail for an order by UUID. Always call this — "
            "count events where the event name contains RETRY to derive retry_count. "
            "The system does not auto-retry, so retry_count reflects manual re-submissions."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order"},
            },
            "required": ["order_id"],
        },
    },
    {
        "name": "get_ledger_account",
        "description": (
            "Get account balance for a user. Returns availableCash, cashBalance, cashOnHold. "
            "Only call in the INSUFFICIENT_FUNDS branch to check if balance has recovered. "
            "Compare availableCash against required = order.quantity * order.limitPrice."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string", "description": "UUID of the user"},
            },
            "required": ["user_id"],
        },
    },
    {
        "name": "create_incident",
        "description": (
            "Create a PagerDuty incident (mock). Call only when ESCALATE or FLAG_SYSTEMIC: "
            "COMPENSATING sagas, retry limit exhausted, service outage, or chronic user failures. "
            "Returns an incident_id (PD-XXXXXXXX)."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "order_id": {"type": "string", "description": "UUID of the order"},
                "severity": {"type": "string", "enum": ["CRITICAL", "HIGH", "MEDIUM"], "description": "Incident severity"},
                "reason":   {"type": "string", "description": "One-sentence reason for escalation"},
            },
            "required": ["order_id", "severity", "reason"],
        },
    },
    {
        "name": "list_recent_failures",
        "description": (
            "Query FAILED orders in the last N minutes. Used in Phase 2 to detect systemic patterns. "
            "Returns count, order_ids, and is_systemic (true if count >= 3). "
            "Always call after Phase 1 triage before branching on failure_reason."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "failure_reason": {"type": "string", "description": "Failure reason code from Phase 1 (for context labeling)"},
                "minutes":        {"type": "integer", "description": "Look-back window in minutes (default 15)"},
            },
            "required": [],
        },
    },
    {
        "name": "get_service_health",
        "description": (
            "Check health status of a named service. Returns HEALTHY, DEGRADED, or DOWN. "
            "Call in Phase 3 for transient failures (NETWORK_ERROR, ORDER_MATCHING_FAILED) "
            "to distinguish an isolated failure from a service-level outage."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "service": {"type": "string", "description": "Service name (e.g. matching-service, ledger-service)"},
            },
            "required": ["service"],
        },
    },
    {
        "name": "get_user_risk_profile",
        "description": (
            "Get a user's failure history over the last 30 days. "
            "Returns total_failed_30d and risk_level (LOW/MEDIUM/HIGH). "
            "Call in Phase 3 for INSUFFICIENT_FUNDS to detect chronic vs one-time failures."
        ),
        "input_schema": {
            "type": "object",
            "properties": {
                "user_id": {"type": "string", "description": "UUID of the user"},
            },
            "required": ["user_id"],
        },
    },
]

DISPATCH = {
    "list_orders":            handle_list_orders,
    "get_order":              handle_get_order,
    "get_saga":               handle_get_saga,
    "query_audit_log":        handle_query_audit_log,
    "get_ledger_account":     handle_get_ledger_account,
    "create_incident":        handle_create_incident,
    "list_recent_failures":   handle_list_recent_failures,
    "get_service_health":     handle_get_service_health,
    "get_user_risk_profile":  handle_get_user_risk_profile,
}


async def call_tool(name: str, args: dict) -> str:
    handler = DISPATCH.get(name)
    if handler is None:
        return f"Unknown tool: {name}"
    results = await handler(args)
    return results[0].text if results else ""


# ---------------------------------------------------------------------------
# CLI entry point (used by kafka_consumer.py)
# ---------------------------------------------------------------------------

async def main():
    system = SYSTEM_TEMPLATE.format(today=date.today())
    if len(sys.argv) > 1:
        question = f"Investigate failed order {sys.argv[1]}"
    else:
        question = input("Question: ")
    print()
    answer = await run_agent(system, TOOLS, call_tool, question)
    print(f"\n{answer}")


if __name__ == "__main__":
    asyncio.run(main())
