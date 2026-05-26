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
)

# ---------------------------------------------------------------------------
# Agent wiring
# ---------------------------------------------------------------------------

SYSTEM_TEMPLATE = """
You are an EquiFlow order failure escalation agent. Today is {today}.

You may be invoked in two ways:
1. A specific order ID is provided — call get_order immediately for that order.
2. A time window question is asked — call list_orders(status=FAILED) to find all failures first.

For every FAILED order, follow this exact sequence:
  1. get_order(order_id)          → confirms status, gets saga_id, user_id, quantity, limitPrice
  2. get_saga(saga_id)            → gets saga.status, saga.failureReason, failed stepName
  3. query_audit_log(order_id)    → always call; count events containing "RETRY" → retry_count

Then apply these decision rules:

DECISION RULES:
- saga.status == COMPENSATING
    → create_incident(severity=CRITICAL, reason="Saga compensation — manual financial reconciliation required")

- failureReason in [NETWORK_ERROR, ORDER_MATCHING_FAILED] and retry_count < 3
    → RETRY — transient failure, re-submission recommended (retry_count from audit log)

- failureReason in [NETWORK_ERROR, ORDER_MATCHING_FAILED] and retry_count >= 3
    → create_incident(severity=HIGH, reason="Retry limit reached after {N} attempts")

- failureReason in [COMPLIANCE_REJECTED]
    → NO_ACTION — expected rejection by compliance; retrying will not succeed

- failureReason == INSUFFICIENT_FUNDS
    → get_ledger_account(user_id)
    → required = order.quantity * order.limitPrice
    → if availableCash >= required: INVESTIGATE (balance recovered, manual retry possible)
    → if availableCash < required:  create_incident(severity=HIGH, reason="Account balance still insufficient")

- any other failureReason
    → INVESTIGATE — unrecognised failure; manual review required

Your final response must state for each order:
- Root cause (exact failureReason from the data)
- Action taken (RETRY / INVESTIGATE / ESCALATE / NO_ACTION)
- Why

Do not speculate beyond what the tools return. If data is missing, say so.

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
      "failure_reason": "<exact failureReason code>",
      "saga_status": "<FAILED|COMPENSATING>",
      "retry_count": <int>,
      "action": "<RETRY|INVESTIGATE|ESCALATE|NO_ACTION>",
      "incident_id": "<PD-XXXX or null>",
      "explanation": "<one sentence>"
    }}
  ],
  "verdict": "<ALL_CLEAR|ESCALATE>"
}}
</findings_json>

verdict: ESCALATE if any incident was created. ALL_CLEAR if all actions are RETRY, INVESTIGATE, or NO_ACTION.
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
            "Create a PagerDuty incident (mock). Call only when ESCALATE is warranted: "
            "COMPENSATING sagas, retry limit exhausted, or insufficient funds confirmed. "
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
]

DISPATCH = {
    "list_orders":         handle_list_orders,
    "get_order":           handle_get_order,
    "get_saga":            handle_get_saga,
    "query_audit_log":     handle_query_audit_log,
    "get_ledger_account":  handle_get_ledger_account,
    "create_incident":     handle_create_incident,
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
