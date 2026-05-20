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
                "Supports filtering by status, ticker symbol, and date range. "
                "Returns paginated results sorted by createdAt descending. "
                "Use this to find a real order_id before calling get_order or query_audit_log."
            ),
            inputSchema={
                "type": "object",
                "properties": {
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
                        "description": "Page size (default 25)",
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
    ]


@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    if name == "get_order":
        r = await authed_get(f"/orders/{arguments['order_id']}")
    elif name == "list_orders":
        params = {k: v for k, v in arguments.items() if v is not None}
        query = "&".join(f"{k}={v}" for k, v in params.items())
        r = await authed_get(f"/orders{'?' + query if query else ''}")
    elif name == "get_saga":
        r = await authed_get(f"/sagas/{arguments['saga_id']}")
    elif name == "query_audit_log":
        r = await authed_get(f"/audit/events/order/{arguments['order_id']}")
    else:
        return [TextContent(type="text", text=f"Unknown tool: {name}")]

    if not r.is_success:
        return [TextContent(type="text", text=f"Error {r.status_code}: {r.text}")]

    return [TextContent(type="text", text=r.text)]


async def main():
    async with stdio_server() as (read, write):
        await server.run(read, write, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
