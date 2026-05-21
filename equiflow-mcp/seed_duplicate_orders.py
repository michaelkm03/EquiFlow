"""
EQ-136: Duplicate Order Detection — Scenario Seed Script

Sends X orders over Y ms across trader1 (60%) and trader2 (40%).
Every (100/Z)th message is a re-submission of the preceding order for that user.

Usage:
    python seed_duplicate_orders.py
    python seed_duplicate_orders.py --messages 100 --duration 30000 --duplicate-pct 10 --duplicate-delay 1s
    python seed_duplicate_orders.py --duplicate-delay 10s  # MEDIUM suspicion
    python seed_duplicate_orders.py --duplicate-delay 60s  # LOW suspicion

Requires market hours bypass enabled on order-service:
    market.hours.bypass=true  (application.yml or MARKET_HOURS_BYPASS=true env var)
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

TICKERS    = ["AAPL", "MSFT", "TSLA", "AMZN", "GOOGL", "META", "NVDA", "NFLX", "AMD",  "INTC"]
SIDES      = ["BUY",  "SELL"]
QUANTITIES = ["5",   "10",   "15",   "20",   "25",    "30",   "35",   "40",   "45",   "50"]


def unique_template(idx: int) -> dict:
    nt, ns, nq = len(TICKERS), len(SIDES), len(QUANTITIES)
    return {
        "ticker":     TICKERS[idx % nt],
        "side":       SIDES[(idx // nt) % ns],
        "quantity":   QUANTITIES[(idx // (nt * ns)) % nq],
        "limitPrice": f"{random.uniform(0.01, 1000.00):.2f}",
        "type":       "LIMIT",
    }


def parse_delay(s: str) -> float:
    s = s.strip()
    if s.endswith("m"):
        return float(s[:-1]) * 60
    if s.endswith("s"):
        return float(s[:-1])
    return float(s)


async def get_token(client: httpx.AsyncClient, username: str, password: str) -> str:
    r = await client.post(
        f"{GATEWAY}/auth/token",
        json={"username": username, "password": password},
    )
    r.raise_for_status()
    return r.json()["token"]


async def post_order(client: httpx.AsyncClient, token: str, body: dict) -> str:
    r = await client.post(
        f"{GATEWAY}/orders",
        json=body,
        headers={"Authorization": f"Bearer {token}"},
        timeout=10.0,
    )
    r.raise_for_status()
    return r.json()["id"]


async def main():
    ap = argparse.ArgumentParser(description="Seed duplicate order scenario for EQ-136")
    ap.add_argument("--messages",        type=int,   default=100,   help="Total orders to send (X)")
    ap.add_argument("--duration",        type=int,   default=30000, help="Window in ms for unique messages (Y)")
    ap.add_argument("--duplicate-pct",   type=float, default=10.0,  help="Percent of X that are duplicates (Z)")
    ap.add_argument("--duplicate-delay", type=str,   default="1s",  help="Delay between original and duplicate: 1s=HIGH / 10s=MEDIUM / 60s=LOW")
    ap.add_argument("--seed",            type=int,   default=42,    help="Random seed for user assignment")
    args = ap.parse_args()

    X         = args.messages
    Y         = args.duration / 1000.0
    dup_pct   = args.duplicate_pct
    dup_delay = parse_delay(args.duplicate_delay)
    dup_step  = round(100.0 / dup_pct)   # every Nth position is a duplicate
    n_dups    = X // dup_step
    n_unique  = X - n_dups
    base_iv   = Y / n_unique              # seconds between unique messages

    # Deterministic interleaved user assignment
    random.seed(args.seed)
    assignment = []
    for u in USERS:
        assignment.extend([u["username"]] * round(X * u["weight"]))
    assignment = assignment[:X]
    random.shuffle(assignment)

    user_counts = {u["username"]: assignment.count(u["username"]) for u in USERS}

    print(f"\n{'═' * 65}")
    print(f"  EQ-136 · DUPLICATE ORDER SCENARIO SEED")
    print(f"{'═' * 65}")
    print(f"  Messages      (X): {X}")
    print(f"  Duration      (Y): {args.duration} ms")
    print(f"  Duplicate %   (Z): {dup_pct:.0f}%  →  {n_dups} duplicates,  {n_unique} unique")
    print(f"  Duplicate step   : every {dup_step}th message")
    print(f"  Duplicate delay  : {args.duplicate_delay}  ({dup_delay:.0f}s)")
    print(f"  Base interval    : {base_iv * 1000:.0f} ms")
    print(f"  Users            : {',  '.join(f'{u} = {c} messages' for u, c in user_counts.items())}")
    print(f"{'─' * 65}\n")

    async with httpx.AsyncClient() as client:
        tokens = {}
        for u in USERS:
            tokens[u["username"]] = await get_token(client, u["username"], u["password"])
        print("  Authenticated ✓\n")
        print(f"  {'#':>4}  {'Type':<10} {'User':<10} {'Ticker':<6} {'Side':<5} {'Qty':>5} {'Price':>8}  Order ID")
        print(f"  {'─' * 4}  {'─' * 10} {'─' * 10} {'─' * 6} {'─' * 5} {'─' * 5} {'─' * 8}  {'─' * 36}")

        last_send  = time.monotonic()
        last_order = {}   # username → {pos, template, order_id}
        tmpl_idx   = 0
        dup_pairs  = []

        for pos in range(1, X + 1):
            username = assignment[pos - 1]
            token    = tokens[username]
            is_dup   = (pos % dup_step == 0)

            elapsed      = time.monotonic() - last_send
            target_sleep = dup_delay if is_dup else base_iv
            await asyncio.sleep(max(0.0, target_sleep - elapsed))

            if is_dup and username in last_order:
                prev      = last_order[username]
                sent_at   = time.monotonic()
                order_id  = await post_order(client, token, prev["template"])
                gap_s     = round(sent_at - prev["sent_at"], 1)
                dup_pairs.append({
                    "pos":      pos,
                    "orig_pos": prev["pos"],
                    "user":     username,
                    "ticker":   prev["template"]["ticker"],
                    "side":     prev["template"]["side"],
                    "qty":      prev["template"]["quantity"],
                    "price":    prev["template"]["limitPrice"],
                    "orig_id":  prev["order_id"],
                    "dup_id":   order_id,
                    "gap_s":    gap_s,
                    "suspicion": "HIGH" if gap_s < 5 else ("MEDIUM" if gap_s <= 30 else "LOW"),
                })
                print(f"  {pos:>4}  {'DUPLICATE':<10} {username:<10} "
                      f"{prev['template']['ticker']:<6} {prev['template']['side']:<5} "
                      f"{prev['template']['quantity']:>5} {prev['template']['limitPrice']:>8}  {order_id}")
            else:
                tmpl     = unique_template(tmpl_idx)
                tmpl_idx += 1
                sent_at  = time.monotonic()
                order_id = await post_order(client, token, tmpl)
                last_order[username] = {"pos": pos, "template": tmpl, "order_id": order_id, "sent_at": sent_at}
                print(f"  {pos:>4}  {'unique':<10} {username:<10} "
                      f"{tmpl['ticker']:<6} {tmpl['side']:<5} "
                      f"{tmpl['quantity']:>5} {tmpl['limitPrice']:>8}  {order_id}")

            last_send = time.monotonic()

    # Summary
    W = 130
    print(f"\n{'═' * W}")
    print(f"  DUPLICATE PAIRS  ({len(dup_pairs)} total)")
    print(f"{'─' * W}")
    print(f"  {'User':<10}  {'Ticker':<6}  {'Side':<4}  {'Qty':>5}  {'Price':>8}  {'Gap':>5}  {'Suspicion':<9}  "
          f"{'Original UUID':<36}  {'Duplicate UUID':<36}")
    print(f"  {'─'*10}  {'─'*6}  {'─'*4}  {'─'*5}  {'─'*8}  {'─'*5}  {'─'*9}  {'─'*36}  {'─'*36}")
    for p in dup_pairs:
        print(f"  {p['user']:<10}  {p['ticker']:<6}  {p['side']:<4}  {p['qty']:>5}  {p['price']:>8}  "
              f"{p['gap_s']:>4.1f}s  {p['suspicion']:<9}  {p['orig_id']:<36}  {p['dup_id']:<36}")
    print(f"{'═' * W}")

    pairs_file = Path(__file__).parent / "scenario_pairs.json"
    pairs_file.write_text(json.dumps({
        "date":  time.strftime("%Y-%m-%d"),
        "delay": args.duplicate_delay,
        "pairs": dup_pairs,
    }, indent=2))
    print(f"\n  Saved {len(dup_pairs)} pairs → {pairs_file.name}")

    print(f"\n  Run the agent:")
    print(f"  python equiflow-mcp/duplicate_agent.py \"Scan today's orders for duplicates\"")
    print(f"\n  Then compare:")
    print(f"  python equiflow-mcp/compare_duplicates.py\n")


if __name__ == "__main__":
    asyncio.run(main())
