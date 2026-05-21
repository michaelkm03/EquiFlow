"""
Cleanup script for EQ-136 duplicate order scenario.

Deletes all orders and related data created by the seed script (trader1 / trader2),
preserving the Flyway seed data (UUIDs starting with b1000000-).

Usage:
    python equiflow-mcp/cleanup_scenario.py           # dry run — shows what would be deleted
    python equiflow-mcp/cleanup_scenario.py --execute  # actually deletes
"""

import argparse
import subprocess
import sys

POSTGRES_CONTAINER = "equiflow-postgres"
POSTGRES_USER      = "equiflow"

# trader1 and trader2 UUIDs (from Flyway seed)
TRADER1_ID = "a1000000-0000-0000-0000-000000000001"
TRADER2_ID = "a1000000-0000-0000-0000-000000000004"

# Flyway-seeded order IDs start with this prefix — never delete these
SEED_PREFIX = "b1000000"


def psql(db: str, sql: str) -> str:
    result = subprocess.run(
        ["docker", "exec", POSTGRES_CONTAINER,
         "psql", "-U", POSTGRES_USER, "-d", db, "-t", "-c", sql],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"  ERROR: {result.stderr.strip()}", file=sys.stderr)
    return result.stdout.strip()


def count(db: str, table: str, where: str) -> int:
    out = psql(db, f"SELECT COUNT(*) FROM {table} WHERE {where};")
    try:
        return int(out.strip())
    except ValueError:
        return 0


def main():
    ap = argparse.ArgumentParser(description="Clean up EQ-136 scenario test data")
    ap.add_argument("--execute", action="store_true", help="Actually delete (default is dry run)")
    args = ap.parse_args()

    dry = not args.execute

    if dry:
        print("\n  DRY RUN — pass --execute to actually delete\n")
    else:
        print("\n  EXECUTING CLEANUP\n")

    user_filter  = f"user_id IN ('{TRADER1_ID}', '{TRADER2_ID}')"
    order_filter = f"{user_filter} AND id::text NOT LIKE '{SEED_PREFIX}%'"

    # ── 1. Collect order IDs to delete ──────────────────────────────────────
    order_ids_raw = psql(
        "equiflow_orders",
        f"SELECT id FROM orders WHERE {order_filter};"
    )
    order_ids = [oid.strip() for oid in order_ids_raw.splitlines() if oid.strip()]
    n_orders = len(order_ids)

    print(f"  {'[DRY RUN] Would delete' if dry else 'Deleting'}: {n_orders} orders (trader1 + trader2, excluding Flyway seed)")

    if n_orders == 0:
        print("\n  Nothing to clean up — already fresh.\n")
        return

    id_list = ", ".join(f"'{oid}'" for oid in order_ids)

    # ── 2. compliance-service ────────────────────────────────────────────────
    n_comp = count("equiflow_compliance", "compliance_results", f"order_id IN ({id_list})")
    print(f"  {'[DRY RUN] Would delete' if dry else 'Deleting'}: {n_comp} compliance results")

    # ── 3. audit-service ─────────────────────────────────────────────────────
    n_audit = count("equiflow_audit", "audit_events", f"order_id IN ({id_list})")
    print(f"  {'[DRY RUN] Would delete' if dry else 'Deleting'}: {n_audit} audit events")

    # ── 4. saga-orchestrator ─────────────────────────────────────────────────
    n_sagas = count("equiflow_saga", "sagas", f"order_id IN ({id_list})")
    print(f"  {'[DRY RUN] Would delete' if dry else 'Deleting'}: {n_sagas} sagas")

    # ── 5. ledger-service ────────────────────────────────────────────────────
    n_holds = count("equiflow_ledger", "account_holds", f"order_id IN ({id_list})")
    n_txns  = count("equiflow_ledger", "ledger_transactions", f"order_id IN ({id_list})")
    print(f"  {'[DRY RUN] Would delete' if dry else 'Deleting'}: {n_holds} ledger holds, {n_txns} ledger transactions")

    if dry:
        print("\n  Run with --execute to apply.\n")
        return

    # ── Execute deletes ──────────────────────────────────────────────────────
    print()
    psql("equiflow_compliance", f"DELETE FROM compliance_results WHERE order_id IN ({id_list});")
    print("  compliance_results ✓")

    psql("equiflow_audit", f"DELETE FROM audit_events WHERE order_id IN ({id_list});")
    print("  audit_events ✓")

    psql("equiflow_saga", f"DELETE FROM saga_steps WHERE saga_id IN (SELECT id FROM sagas WHERE order_id IN ({id_list}));")
    psql("equiflow_saga", f"DELETE FROM sagas WHERE order_id IN ({id_list});")
    print("  sagas + saga_steps ✓")

    psql("equiflow_ledger", f"DELETE FROM account_holds WHERE order_id IN ({id_list});")
    psql("equiflow_ledger", f"DELETE FROM ledger_transactions WHERE order_id IN ({id_list});")
    print("  account_holds + ledger_transactions ✓")

    psql("equiflow_orders", f"DELETE FROM orders WHERE id IN ({id_list});")
    print(f"  orders ✓ ({n_orders} deleted)")

    print("\n  Done. Stack is ready for the next scenario run.\n")


if __name__ == "__main__":
    main()
