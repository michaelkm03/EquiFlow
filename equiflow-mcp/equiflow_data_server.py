import asyncio
import httpx
from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import Tool, TextContent

GATEWAY = "http://localhost:8080"
_token: str | None = None

server = Server("equiflow-data")


async def get_token() -> str:
    async with httpx.AsyncClient() as client:
        r = await client.post(
            f"{GATEWAY}/auth/token",
            json={"username": "bot-operator1", "password": "password123"},
        )
        r.raise_for_status()
        return r.json()["token"]


async def authed_get(path: str) -> httpx.Response:
    global _token
    if _token is None:
        _token = await get_token()
    async with httpx.AsyncClient() as client:
        r = await client.get(
            f"{GATEWAY}{path}",
            headers={"Authorization": f"Bearer {_token}"},
        )
        if r.status_code == 401:
            # Token expired — refresh once and retry
            _token = await get_token()
            r = await client.get(
                f"{GATEWAY}{path}",
                headers={"Authorization": f"Bearer {_token}"},
            )
        return r


@server.list_tools()
async def list_tools() -> list[Tool]:
    return [
        Tool(
            name="get_order",
            description=(
                "Get a single EquiFlow order by UUID. "
                "Returns current status, order type, ticker, quantity, saga ID, and timestamps. "
                "Use this first when investigating a stuck or failed order."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "UUID of the order to retrieve",
                    }
                },
                "required": ["order_id"],
            },
        ),
        Tool(
            name="get_saga",
            description=(
                "Get the saga execution trace for a distributed transaction by saga UUID. "
                "Returns each saga step with its status, failure reason, and retry count. "
                "Use after get_order to identify which step in the transaction failed and why."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "saga_id": {
                        "type": "string",
                        "description": "UUID of the saga to retrieve",
                    }
                },
                "required": ["saga_id"],
            },
        ),
        Tool(
            name="list_orders",
            description=(
                "List orders from the EquiFlow database with optional filtering. "
                "Supports filtering by userId, status, ticker symbol, and date range. "
                "Returns paginated results sorted by createdAt descending. "
                "Use size=100 and paginate with the page param for bulk scans."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "userId": {
                        "type": "string",
                        "description": "Filter by user UUID",
                    },
                    "status": {
                        "type": "string",
                        "enum": [
                            "PENDING",
                            "COMPLIANCE_CHECK",
                            "OPEN",
                            "FILLED",
                            "PARTIALLY_FILLED",
                            "CANCELLED",
                            "REJECTED",
                            "FAILED",
                            "PENDING_TRIGGER",
                            "TRIGGERED",
                        ],
                        "description": "Filter by order status",
                    },
                    "ticker": {
                        "type": "string",
                        "description": "Filter by ticker symbol (e.g. AAPL)",
                    },
                    "from": {
                        "type": "string",
                        "description": "Start date filter in YYYY-MM-DD format",
                    },
                    "to": {
                        "type": "string",
                        "description": "End date filter in YYYY-MM-DD format",
                    },
                    "page": {
                        "type": "integer",
                        "description": "Page number (0-based, default 0)",
                    },
                    "size": {
                        "type": "integer",
                        "description": "Page size (default 25, use 100 for bulk scans)",
                    },
                },
                "required": [],
            },
        ),
        Tool(
            name="query_audit_log",
            description=(
                "Get the full append-only audit trail for a specific order by UUID. "
                "Returns every state transition, retry attempt, and timestamp in chronological order. "
                "Use after get_saga to determine how many retries occurred and when the last attempt happened."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "UUID of the order to retrieve audit events for",
                    }
                },
                "required": ["order_id"],
            },
        ),
        Tool(
            name="get_compliance_result",
            description=(
                "Get the compliance check result for a specific order by UUID. "
                "Returns the overall result (APPROVED or REJECTED), all violations with their type and reason, and the check timestamp. "
                "Use after list_orders to retrieve the specific violation type and failure reason for a REJECTED order. "
                "Do not call for orders with status other than REJECTED — only rejected orders have meaningful violations."
            ),
            inputSchema={
                "type": "object",
                "properties": {
                    "order_id": {
                        "type": "string",
                        "description": "UUID of the order to retrieve the compliance result for",
                    }
                },
                "required": ["order_id"],
            },
        ),
    ]


async def _ok(r: httpx.Response) -> list[TextContent]:
    if not r.is_success:
        return [TextContent(type="text", text=f"Error {r.status_code}: {r.text}")]
    return [TextContent(type="text", text=r.text)]


async def handle_get_order(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/orders/{args['order_id']}"))


async def handle_list_orders(args: dict) -> list[TextContent]:
    params = {k: v for k, v in args.items() if v is not None}
    query = "&".join(f"{k}={v}" for k, v in params.items())
    return await _ok(await authed_get(f"/orders/internal/all{'?' + query if query else ''}"))


async def handle_get_saga(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/saga/{args['saga_id']}"))


async def handle_query_audit_log(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/audit/events/order/{args['order_id']}"))


async def handle_get_compliance_result(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/compliance/results/order/{args['order_id']}"))


async def handle_get_ledger_account(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/ledger/accounts/{args['user_id']}"))


async def handle_create_incident(args: dict) -> list[TextContent]:
    import uuid as _uuid
    import json as _json
    incident_id = f"PD-{str(_uuid.uuid4())[:8].upper()}"
    payload = {
        "incident_id": incident_id,
        "order_id": args["order_id"],
        "severity": args.get("severity", "HIGH"),
        "reason": args["reason"],
        "status": "triggered",
        "note": "Mock incident — in production this calls PagerDuty Events API v2",
    }
    return [TextContent(type="text", text=_json.dumps(payload))]


async def handle_list_recent_failures(args: dict) -> list[TextContent]:
    """Query FAILED orders in the last N minutes. Filters client-side by createdAt."""
    import json as _json
    from datetime import datetime as _dt, timezone, timedelta as _td, date as _date

    minutes = int(args.get("minutes", 15))
    failure_reason = args.get("failure_reason", "")

    yesterday = _date.today() - _td(days=1)
    tomorrow  = _date.today() + _td(days=1)
    r = await authed_get(f"/orders/internal/all?status=FAILED&from={yesterday}&to={tomorrow}&size=100")
    if not r.is_success:
        return [TextContent(type="text", text=f"Error {r.status_code}: {r.text}")]

    try:
        orders  = r.json().get("content", [])
        cutoff  = _dt.now(timezone.utc) - _td(minutes=minutes)
        recent  = []
        for o in orders:
            try:
                s = o.get("createdAt", "").replace("Z", "+00:00")
                if "+" not in s:
                    s += "+00:00"
                if _dt.fromisoformat(s) >= cutoff:
                    recent.append(o)
            except Exception:
                pass
        count  = len(recent)
        result = {
            "failure_reason_filter": failure_reason or "ALL",
            "window_minutes":        minutes,
            "count":                 count,
            "order_ids":             [o.get("id") for o in recent],
            "systemic_threshold":    3,
            "is_systemic":           count >= 3,
        }
    except Exception as e:
        result = {
            "failure_reason_filter": failure_reason or "ALL",
            "window_minutes": minutes,
            "count": 0, "order_ids": [], "is_systemic": False, "error": str(e),
        }
    return [TextContent(type="text", text=_json.dumps(result))]


# Override service statuses here to demo FLAG_SYSTEMIC path (empty = all HEALTHY)
_SERVICE_HEALTH: dict[str, str] = {}

async def handle_get_service_health(args: dict) -> list[TextContent]:
    """Mock service health check. Returns HEALTHY|DEGRADED|DOWN for a named service."""
    import json as _json
    service = args.get("service", "unknown")
    result  = {
        "service": service,
        "status":  _SERVICE_HEALTH.get(service, "HEALTHY"),
        "note":    "Mock — in production calls real status endpoint",
    }
    return [TextContent(type="text", text=_json.dumps(result))]


async def handle_get_user_risk_profile(args: dict) -> list[TextContent]:
    """Count FAILED orders for a user in the last 30 days and classify risk level."""
    import json as _json
    from datetime import date as _date, timedelta as _td

    user_id    = args["user_id"]
    from_date  = _date.today() - _td(days=30)
    to_date    = _date.today() + _td(days=1)
    r = await authed_get(
        f"/orders/internal/all?status=FAILED&userId={user_id}&from={from_date}&to={to_date}&size=100"
    )
    try:
        if not r.is_success:
            raise ValueError(f"HTTP {r.status_code}")
        data         = r.json()
        total_failed = data.get("totalElements", len(data.get("content", [])))
        risk_level   = "HIGH" if total_failed >= 10 else "MEDIUM" if total_failed >= 5 else "LOW"
        profile      = {
            "user_id":         user_id,
            "total_failed_30d": total_failed,
            "risk_level":      risk_level,
            "window_days":     30,
        }
    except Exception as e:
        profile = {
            "user_id": user_id, "total_failed_30d": 0,
            "risk_level": "UNKNOWN", "window_days": 30, "error": str(e),
        }
    return [TextContent(type="text", text=_json.dumps(profile))]


HANDLERS = {
    "get_order":               handle_get_order,
    "list_orders":             handle_list_orders,
    "get_saga":                handle_get_saga,
    "query_audit_log":         handle_query_audit_log,
    "get_compliance_result":   handle_get_compliance_result,
    "get_ledger_account":      handle_get_ledger_account,
    "create_incident":         handle_create_incident,
    "list_recent_failures":    handle_list_recent_failures,
    "get_service_health":      handle_get_service_health,
    "get_user_risk_profile":   handle_get_user_risk_profile,
}


@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    handler = HANDLERS.get(name)
    if handler is None:
        return [TextContent(type="text", text=f"Unknown tool: {name}")]
    return await handler(arguments)


async def main():
    async with stdio_server() as (read, write):
        await server.run(read, write, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
