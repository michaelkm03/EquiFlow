"""
EQ-132: Failure Escalation Agent — Test Data Seed Script

Default mode — covers all standard escalation branches:
  c1000000-...001  NETWORK_ERROR        retry_count=0, 30 min old  → RETRY
  c1000000-...002  COMPLIANCE_REJECTED                             → NO_ACTION
  c1000000-...003  INSUFFICIENT_FUNDS   balance<req               → NO_ACTION / ESCALATE
  c1000000-...004  LEDGER_DEBIT_FAILED  saga=COMPENSATING         → ESCALATE

--systemic mode — triggers FLAG_SYSTEMIC via Phase 2 pattern detection:
  Inserts 3 NETWORK_ERROR orders timestamped NOW() (within the 15-min window).
  list_recent_failures will return count=3, is_systemic=true → FLAG_SYSTEMIC verdict.
  c1100000-...001/002/003  NETWORK_ERROR  fresh timestamps

Usage:
    python equiflow-mcp/seed_failed_orders.py
    python equiflow-mcp/seed_failed_orders.py --systemic
    python equiflow-mcp/seed_failed_orders.py --clean
    python equiflow-mcp/seed_failed_orders.py --systemic --clean
"""
import argparse
import subprocess
import sys

CONTAINER  = "equiflow-postgres"
DB_USER    = "equiflow"
ORDERS_DB  = "equiflow_orders"
SAGA_DB    = "equiflow_saga"

# Fixed UUIDs — safe to re-run (INSERT ... ON CONFLICT DO NOTHING)
ORDERS = [
    ("c1000000-0000-0000-0000-000000000001",
     "a1000000-0000-0000-0000-000000000001",  # trader1
     "AAPL", "BUY", "LIMIT", 10, 150.00,
     "d1000000-0000-0000-0000-000000000001",  # saga_id
     "NETWORK_ERROR"),
    ("c1000000-0000-0000-0000-000000000002",
     "a1000000-0000-0000-0000-000000000001",  # trader1
     "TSLA", "BUY", "LIMIT", 5, 250.00,
     "d1000000-0000-0000-0000-000000000002",
     "COMPLIANCE_REJECTED"),
    ("c1000000-0000-0000-0000-000000000003",
     "a1000000-0000-0000-0000-000000000001",  # trader1
     "NVDA", "BUY", "LIMIT", 100, 900.00,    # required=$90,000 — exceeds trader1 balance
     "d1000000-0000-0000-0000-000000000003",
     "INSUFFICIENT_FUNDS"),
    ("c1000000-0000-0000-0000-000000000004",
     "a1000000-0000-0000-0000-000000000004",  # trader2
     "MSFT", "BUY", "LIMIT", 20, 420.00,
     "d1000000-0000-0000-0000-000000000004",
     "LEDGER_DEBIT_FAILED"),                  # saga status=COMPENSATING → ESCALATE
]

SAGA_STATUSES = {
    "d1000000-0000-0000-0000-000000000001": "FAILED",
    "d1000000-0000-0000-0000-000000000002": "FAILED",
    "d1000000-0000-0000-0000-000000000003": "FAILED",
    "d1000000-0000-0000-0000-000000000004": "COMPENSATING",  # → always ESCALATE
}

SAGA_STEPS = [
    # (saga_id, step_number, step_name, status, error_message)
    ("d1000000-0000-0000-0000-000000000001", 2, "ORDER_MATCHING", "FAILED",
     "Connection refused: matching service timeout"),
    ("d1000000-0000-0000-0000-000000000002", 1, "COMPLIANCE_CHECK", "FAILED",
     "Compliance rejected: wash sale violation"),
    ("d1000000-0000-0000-0000-000000000003", 3, "LEDGER_DEBIT", "FAILED",
     "Insufficient funds: available 5000.00, required 90000.00"),
    ("d1000000-0000-0000-0000-000000000004", 3, "LEDGER_DEBIT", "FAILED",
     "Ledger service unavailable"),
]

# --systemic: 3 fresh NETWORK_ERROR orders within the 15-min detection window.
# Tickers/users are distinct so they look like independent failures, not one user retrying.
SYSTEMIC_ORDERS = [
    ("c1100000-0000-0000-0000-000000000001",
     "a1000000-0000-0000-0000-000000000001",  # trader1
     "AAPL", "BUY", "LIMIT", 10, 150.00,
     "d1100000-0000-0000-0000-000000000001",
     "NETWORK_ERROR"),
    ("c1100000-0000-0000-0000-000000000002",
     "a1000000-0000-0000-0000-000000000004",  # trader2
     "GOOGL", "BUY", "LIMIT", 5, 175.00,
     "d1100000-0000-0000-0000-000000000002",
     "NETWORK_ERROR"),
    ("c1100000-0000-0000-0000-000000000003",
     "a1000000-0000-0000-0000-000000000005",  # trader3
     "AMZN", "BUY", "LIMIT", 3, 200.00,
     "d1100000-0000-0000-0000-000000000003",
     "NETWORK_ERROR"),
]

SYSTEMIC_SAGA_STEPS = [
    ("d1100000-0000-0000-0000-000000000001", 2, "ORDER_MATCHING", "FAILED",
     "Connection refused: matching service timeout"),
    ("d1100000-0000-0000-0000-000000000002", 2, "ORDER_MATCHING", "FAILED",
     "Connection refused: matching service timeout"),
    ("d1100000-0000-0000-0000-000000000003", 2, "ORDER_MATCHING", "FAILED",
     "Connection refused: matching service timeout"),
]


def psql(db: str, sql: str):
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER,
         "psql", "-U", DB_USER, "-d", db, "-c", sql],
        capture_output=True, text=True,
    )
    if result.returncode != 0:
        print(f"  ERROR: {result.stderr.strip()}", file=sys.stderr)
    else:
        print(f"  OK: {result.stdout.strip()}")


def clean(systemic: bool = False):
    if systemic:
        print("\nCleaning systemic seeded rows...")
        order_ids = ", ".join(f"'{o[0]}'" for o in SYSTEMIC_ORDERS)
        saga_ids  = ", ".join(f"'{o[7]}'" for o in SYSTEMIC_ORDERS)
    else:
        print("\nCleaning seeded rows...")
        order_ids = ", ".join(f"'{o[0]}'" for o in ORDERS)
        saga_ids  = ", ".join(f"'{s}'" for s in SAGA_STATUSES)

    psql(SAGA_DB,   f"DELETE FROM saga_steps WHERE saga_id IN ({saga_ids});")
    psql(SAGA_DB,   f"DELETE FROM sagas      WHERE id       IN ({saga_ids});")
    psql(ORDERS_DB, f"DELETE FROM orders     WHERE id       IN ({order_ids});")
    print("Done.\n")


def seed():
    print("\nSeeding FAILED orders...")

    for (oid, uid, ticker, side, typ, qty, price, saga_id, _reason) in ORDERS:
        psql(ORDERS_DB, f"""
            INSERT INTO orders
              (id, user_id, ticker, side, type, quantity, limit_price,
               trigger_price, filled_price, filled_qty, status, saga_id,
               created_at, updated_at)
            VALUES
              ('{oid}', '{uid}', '{ticker}', '{side}', '{typ}',
               {qty}, {price}, NULL, NULL, NULL, 'FAILED', '{saga_id}',
               NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '30 minutes')
            ON CONFLICT (id) DO NOTHING;
        """)

    print("\nSeeding sagas...")
    for (oid, uid, ticker, side, typ, qty, price, saga_id, failure_reason) in ORDERS:
        status = SAGA_STATUSES[saga_id]
        psql(SAGA_DB, f"""
            INSERT INTO sagas
              (id, order_id, user_id, status, current_step,
               failure_reason, started_at, completed_at)
            VALUES
              ('{saga_id}', '{oid}', '{uid}', '{status}', 3,
               '{failure_reason}', NOW() - INTERVAL '35 minutes',
               NOW() - INTERVAL '30 minutes')
            ON CONFLICT (id) DO NOTHING;
        """)

    print("\nSeeding saga steps...")
    for (saga_id, step_num, step_name, step_status, error_msg) in SAGA_STEPS:
        safe_msg = error_msg.replace("'", "''")
        psql(SAGA_DB, f"""
            INSERT INTO saga_steps
              (id, saga_id, step_number, step_name, status,
               response_payload, error_message, executed_at)
            VALUES
              (gen_random_uuid(), '{saga_id}', {step_num}, '{step_name}',
               '{step_status}', NULL, '{safe_msg}',
               NOW() - INTERVAL '31 minutes')
            ON CONFLICT DO NOTHING;
        """)

    print("\nSeeded orders summary:")
    print(f"  {'UUID suffix':<8}  {'ticker':<6}  {'failure_reason':<20}  expected action")
    print(f"  {'-'*8}  {'-'*6}  {'-'*20}  {'-'*20}")
    for (oid, _uid, ticker, _s, _t, _q, _p, saga_id, reason) in ORDERS:
        suffix = oid[-3:]
        status = SAGA_STATUSES[saga_id]
        if status == "COMPENSATING":
            action = "ESCALATE (compensating)"
        elif reason == "NETWORK_ERROR":
            action = "RETRY (retry_count=0)"
        elif reason == "COMPLIANCE_REJECTED":
            action = "NO_ACTION"
        elif reason == "INSUFFICIENT_FUNDS":
            action = "NO_ACTION (one-time shortfall)"
        else:
            action = "INVESTIGATE"
        print(f"  ...{suffix}  {ticker:<6}  {reason:<20}  {action}")
    print()


def seed_systemic():
    """Insert 3 NETWORK_ERROR orders with NOW() timestamps so list_recent_failures
    returns is_systemic=true and the agent routes to FLAG_SYSTEMIC."""
    print("\nSeeding systemic FAILED orders (fresh timestamps — within 15-min window)...")

    for (oid, uid, ticker, side, typ, qty, price, saga_id, failure_reason) in SYSTEMIC_ORDERS:
        psql(ORDERS_DB, f"""
            INSERT INTO orders
              (id, user_id, ticker, side, type, quantity, limit_price,
               trigger_price, filled_price, filled_qty, status, saga_id,
               created_at, updated_at)
            VALUES
              ('{oid}', '{uid}', '{ticker}', '{side}', '{typ}',
               {qty}, {price}, NULL, NULL, NULL, 'FAILED', '{saga_id}',
               NOW() - INTERVAL '2 minutes', NOW() - INTERVAL '2 minutes')
            ON CONFLICT (id) DO NOTHING;
        """)

    print("\nSeeding systemic sagas...")
    for (oid, uid, ticker, side, typ, qty, price, saga_id, failure_reason) in SYSTEMIC_ORDERS:
        psql(SAGA_DB, f"""
            INSERT INTO sagas
              (id, order_id, user_id, status, current_step,
               failure_reason, started_at, completed_at)
            VALUES
              ('{saga_id}', '{oid}', '{uid}', 'FAILED', 2,
               '{failure_reason}', NOW() - INTERVAL '3 minutes',
               NOW() - INTERVAL '2 minutes')
            ON CONFLICT (id) DO NOTHING;
        """)

    print("\nSeeding systemic saga steps...")
    for (saga_id, step_num, step_name, step_status, error_msg) in SYSTEMIC_SAGA_STEPS:
        safe_msg = error_msg.replace("'", "''")
        psql(SAGA_DB, f"""
            INSERT INTO saga_steps
              (id, saga_id, step_number, step_name, status,
               response_payload, error_message, executed_at)
            VALUES
              (gen_random_uuid(), '{saga_id}', {step_num}, '{step_name}',
               '{step_status}', NULL, '{safe_msg}',
               NOW() - INTERVAL '2 minutes')
            ON CONFLICT DO NOTHING;
        """)

    print("\nSystemic orders seeded:")
    print(f"  {'UUID suffix':<8}  {'ticker':<6}  {'failure_reason':<20}  expected action")
    print(f"  {'-'*8}  {'-'*6}  {'-'*20}  {'-'*20}")
    for (oid, _uid, ticker, *_, reason) in SYSTEMIC_ORDERS:
        print(f"  ...{oid[-3:]}  {ticker:<6}  {reason:<20}  FLAG_SYSTEMIC (3 recent failures)")
    print()
    print("  NOTE: timestamps are NOW() - 2 min. Run the agent within 13 minutes or re-seed.")
    print()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--systemic", action="store_true",
                    help="Seed 3 fresh NETWORK_ERROR orders to trigger FLAG_SYSTEMIC via Phase 2 pattern detection")
    ap.add_argument("--clean", action="store_true",
                    help="Remove seeded rows and exit (combine with --systemic to clean systemic rows)")
    args = ap.parse_args()

    if args.clean:
        clean(systemic=args.systemic)
    elif args.systemic:
        seed_systemic()
    else:
        seed()


if __name__ == "__main__":
    main()
