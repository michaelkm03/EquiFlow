"""
EQ-136: Duplicate Order Detection - Scenario Seed Script

Each duplicate pair is placed atomically: fresh original -> sleep(target_gap) -> duplicate.
This guarantees the gap classification matches the configured level regardless of
other orders or API latency variance.

Usage:
    python seed_duplicate_orders.py
    python seed_duplicate_orders.py --messages 20 --duplicate-delay 0.1s --max-delay 0.5s
"""

import argparse
import asyncio
import json
import random
import time
from pathlib import Path

import httpx

GATEWAY = "http://localhost:8080"

USERS = [
    {"username": "trader1", "password": "password123", "weight": 0.60},
    {"username": "trader2", "password": "password123", "weight": 0.40},
]

TICKERS    = ["AAPL", "MSFT", "TSLA", "AMZN", "GOOGL", "META", "NVDA", "NFLX", "AMD", "INTC"]
SIDES      = ["BUY", "SELL"]
QUANTITIES = ["5", "10", "15", "20", "25", "30", "35", "40", "45", "50"]

_price_rng = random.Random()  # unseeded — different prices every run
_gap_rng   = random.Random()  # unseeded — different gaps every run


def unique_template(idx: int) -> dict:
    nt, ns, nq = len(TICKERS), len(SIDES), len(QUANTITIES)
    return {
        "ticker":     TICKERS[idx % nt],
        "side":       SIDES[(idx // nt) % ns],
        "quantity":   QUANTITIES[(idx // (nt * ns)) % nq],
        "limitPrice": f"{_price_rng.uniform(0.01, 1000.00):.2f}",
        "type":       "LIMIT",
    }


def classify(gap_s: float) -> str:
    if gap_s < 1:
        return "HIGH"
    if gap_s <= 5:
        return "MEDIUM"
    return "LOW"


def parse_delay(s: str) -> float:
    s = s.strip()
    if s.endswith("m"):
        return float(s[:-1]) * 60
    if s.endswith("s"):
        return float(s[:-1])
    return float(s)


async def get_token(client: httpx.AsyncClient, username: str, password: str) -> str:
    r = await client.post(f"{GATEWAY}/auth/token",
                          json={"username": username, "password": password})
    r.raise_for_status()
    return r.json()["token"]


async def post_order(client: httpx.AsyncClient, token: str, body: dict) -> str:
    r = await client.post(f"{GATEWAY}/orders", json=body,
                          headers={"Authorization": f"Bearer {token}"}, timeout=10.0)
    r.raise_for_status()
    return r.json()["id"]


async def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--messages",        type=int,   default=20)
    ap.add_argument("--duration",        type=int,   default=6000,  help="Window ms for unique orders")
    ap.add_argument("--duplicate-pct",   type=float, default=10.0)
    ap.add_argument("--duplicate-delay", type=str,   default="0.1s", help="Min gap between original and dup")
    ap.add_argument("--max-delay",       type=str,   default="",     help="Max gap; defaults to duplicate-delay")
    ap.add_argument("--seed",            type=int,   default=42,     help="Seed for user assignment only")
    args = ap.parse_args()

    X         = args.messages
    Y         = args.duration / 1000.0
    dup_delay = parse_delay(args.duplicate_delay)
    max_delay = parse_delay(args.max_delay) if args.max_delay else dup_delay
    dup_step  = round(100.0 / args.duplicate_pct)
    n_dups    = X // dup_step
    n_unique  = X - n_dups
    base_iv   = Y / max(n_unique, 1)

    # Deterministic user assignment — prices and gaps use separate unseeded RNGs
    _assign_rng = random.Random(args.seed)
    assignment = []
    for u in USERS:
        assignment.extend([u["username"]] * round(X * u["weight"]))
    assignment = assignment[:X]
    _assign_rng.shuffle(assignment)

    print(f"\n  EQ-136  {n_dups} pairs / {n_unique} unique  "
          f"gap {args.duplicate_delay}-{args.max_delay or args.duplicate_delay}\n")
    print(f"  {'#':>4}  {'type':<10} {'user':<10} {'ticker':<6} {'side':<5} "
          f"{'qty':>5} {'price':>8}  {'gap':>6}  id")
    print(f"  {'-'*4}  {'-'*10} {'-'*10} {'-'*6} {'-'*5} {'-'*5} {'-'*8}  {'-'*6}  {'-'*36}")

    async with httpx.AsyncClient() as client:
        tokens = {u["username"]: await get_token(client, u["username"], u["password"])
                  for u in USERS}

        seed_start = time.monotonic()
        last_send  = time.monotonic()
        inserted   = 0
        tmpl_idx   = 0
        dup_pairs  = []

        for pos in range(1, X + 1):
            username = assignment[pos - 1]
            token    = tokens[username]
            is_dup   = (pos % dup_step == 0)

            # Space unique orders evenly; dup slots add their own pair inline
            elapsed = time.monotonic() - last_send
            await asyncio.sleep(max(0.0, base_iv - elapsed))

            if is_dup:
                # ── Atomic pair: original → sleep(target_gap) → duplicate ──
                tmpl     = unique_template(tmpl_idx); tmpl_idx += 1
                orig_id  = await post_order(client, token, tmpl)
                orig_t   = time.monotonic()  # capture AFTER call so gap_s = sleep only
                inserted += 1
                print(f"  {inserted:>4}  {'original':<10} {username:<10} "
                      f"{tmpl['ticker']:<6} {tmpl['side']:<5} "
                      f"{tmpl['quantity']:>5} {tmpl['limitPrice']:>8}  {'':>6}  {orig_id}")

                target_gap = _gap_rng.uniform(dup_delay, max_delay)
                await asyncio.sleep(target_gap)

                dup_t    = time.monotonic()
                dup_id   = await post_order(client, token, tmpl)
                inserted += 1
                gap_s    = round(dup_t - orig_t, 2)
                suspicion = classify(gap_s)
                dup_pairs.append({
                    "user": username, "ticker": tmpl["ticker"], "side": tmpl["side"],
                    "qty": tmpl["quantity"], "price": tmpl["limitPrice"],
                    "orig_id": orig_id, "dup_id": dup_id,
                    "gap_s": gap_s, "suspicion": suspicion,
                })
                print(f"  {inserted:>4}  {'DUPLICATE':<10} {username:<10} "
                      f"{tmpl['ticker']:<6} {tmpl['side']:<5} "
                      f"{tmpl['quantity']:>5} {tmpl['limitPrice']:>8}  {gap_s:>5.2f}s  {dup_id}  [{suspicion}]")
            else:
                tmpl     = unique_template(tmpl_idx); tmpl_idx += 1
                order_id = await post_order(client, token, tmpl)
                inserted += 1
                print(f"  {inserted:>4}  {'unique':<10} {username:<10} "
                      f"{tmpl['ticker']:<6} {tmpl['side']:<5} "
                      f"{tmpl['quantity']:>5} {tmpl['limitPrice']:>8}  {'':>6}  {order_id}")

            last_send = time.monotonic()

            # Progress at each 10% milestone
            total_expected = n_unique + 2 * n_dups
            pct = int(inserted / total_expected * 100)
            if inserted % max(1, total_expected // 10) == 0 or inserted == total_expected:
                elapsed_total = time.monotonic() - seed_start
                if inserted < total_expected:
                    eta_s = elapsed_total / inserted * (total_expected - inserted)
                    print(f"  {pct}%  {inserted}/{total_expected} inserted  ETA {int(eta_s)}s")
                else:
                    print(f"  {pct}%  {inserted}/{total_expected} inserted  done")

    # Summary
    print(f"\n  {len(dup_pairs)} pairs seeded:")
    for p in dup_pairs:
        print(f"    {p['suspicion']:<6}  {p['user']:<10}  {p['ticker']:<6} {p['side']:<5} "
              f"qty={p['qty']}  gap={p['gap_s']}s")

    pairs_file = Path(__file__).parent / "scenario_pairs.json"
    pairs_file.write_text(json.dumps({
        "date": time.strftime("%Y-%m-%d"), "delay": args.duplicate_delay, "pairs": dup_pairs,
    }, indent=2))
    print(f"\n  Saved -> {pairs_file.name}\n")


if __name__ == "__main__":
    asyncio.run(main())
