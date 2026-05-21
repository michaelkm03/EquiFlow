"""
Reconcile seed-inserted duplicate pairs against agent findings.

Usage:
    python compare_duplicates.py
    python compare_duplicates.py --seed scenario_pairs.json --agent agent_findings.json
"""

import argparse
import json
from datetime import date
from pathlib import Path


def _load(path: Path) -> dict:
    if not path.exists():
        raise FileNotFoundError(
            f"{path.name} not found.\n"
            f"  seed:  python seed_duplicate_orders.py\n"
            f"  agent: python duplicate_agent.py \"Scan today's orders for duplicates\""
        )
    return json.loads(path.read_text())


def _fmt_qty(v) -> str:
    try:
        return str(int(float(str(v))))
    except (ValueError, TypeError):
        return str(v)


def _fmt_price(v) -> str:
    try:
        return f"{float(str(v)):.2f}"
    except (ValueError, TypeError):
        return str(v)


def _key(p: dict) -> frozenset:
    return frozenset({p["orig_id"], p["dup_id"]})


def main():
    ap = argparse.ArgumentParser(description="Reconcile seed pairs vs agent findings")
    ap.add_argument("--seed",  default="scenario_pairs.json",  help="JSON written by seed script")
    ap.add_argument("--agent", default="agent_findings.json",  help="JSON written by agent")
    args = ap.parse_args()

    base = Path(__file__).parent
    seed_data  = _load(base / args.seed)
    agent_data = _load(base / args.agent)

    seed_pairs  = seed_data.get("pairs", [])
    agent_pairs = agent_data.get("pairs", [])

    agent_index = {_key(p): p for p in agent_pairs}
    seed_keys   = {_key(p) for p in seed_pairs}

    rows: list[tuple[str, dict]] = []
    for p in seed_pairs:
        rows.append(("FOUND" if _key(p) in agent_index else "MISSED", p))
    for p in agent_pairs:
        if _key(p) not in seed_keys:
            rows.append(("EXTRA", p))

    n_found  = sum(1 for s, _ in rows if s == "FOUND")
    n_missed = sum(1 for s, _ in rows if s == "MISSED")
    n_extra  = sum(1 for s, _ in rows if s == "EXTRA")
    total    = len(seed_pairs)
    rate     = f"{100 * n_found / total:.1f}%" if total else "—"

    STATUS_LABEL = {
        "FOUND":  "✓ FOUND   ",
        "MISSED": "✗ MISSED  ",
        "EXTRA":  "! EXTRA   ",
    }

    W = 165
    run_date  = seed_data.get("date", str(date.today()))
    run_delay = seed_data.get("delay", "?")

    print(f"\n{'═' * W}")
    print(f"  DUPLICATE RECONCILIATION  —  {run_date}  [delay: {run_delay}]")
    print(f"{'═' * W}")
    print(f"  {'#':>3}  {'Status':<11}  {'User':<10}  {'Ticker':<6}  {'Side':<4}  "
          f"{'Qty':>5}  {'Price':>8}  {'Gap':>5}  {'Suspicion':<9}  "
          f"{'Original UUID':<36}  {'Duplicate UUID':<36}")
    print(f"  {'─'*3}  {'─'*11}  {'─'*10}  {'─'*6}  {'─'*4}  "
          f"{'─'*5}  {'─'*8}  {'─'*5}  {'─'*9}  {'─'*36}  {'─'*36}")

    for i, (status, p) in enumerate(rows, 1):
        user   = p.get("user") or (p.get("user_id", "")[:8] + "...")
        dup_id = p.get("dup_id", "—")
        gap_s  = p.get("gap_s", 0.0)
        print(f"  {i:>3}  {STATUS_LABEL[status]}  {user:<10}  {p['ticker']:<6}  {p['side']:<4}  "
              f"{_fmt_qty(p['qty']):>5}  {_fmt_price(p['price']):>8}  {gap_s:>4.1f}s  "
              f"{p.get('suspicion', '?'):<9}  {p['orig_id']:<36}  {dup_id:<36}")

    print(f"{'═' * W}")
    print(f"  Seed inserted : {total}    Agent found : {n_found}    "
          f"Missed : {n_missed}    False positives : {n_extra}    "
          f"Detection rate : {rate}")
    print(f"{'═' * W}\n")


if __name__ == "__main__":
    main()
