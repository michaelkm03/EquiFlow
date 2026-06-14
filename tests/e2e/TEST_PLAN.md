# EquiFlow — Test Plan & CI Stage Strategy

## Overview

This document defines what tests run at each stage of the CI/CD pipeline,
why they run there, how failures are handled, and exactly which test covers
each case. The goal is fast feedback on PRs without sacrificing release
confidence on merge.

---

## Test Taxonomy

| Tag | Scope | Speed | Services Required |
|-----|-------|-------|-------------------|
| `@smoke` | Individual service health | < 60s | Auth + Market Data only |
| `@integration` | Per-service API contract | 2–4 min | All services |
| `@e2e` | Full cross-service saga | 3–5 min | All services |
| _(none)_ | Full regression | 8–12 min | All services |

Run a specific suite:
```bash
npx playwright test --project=smoke
npx playwright test --project=integration
npx playwright test --project=e2e
npx playwright test --project=regression   # runs everything
```

---

## Stage 1 — Build & Unit Tests

**Trigger:** Every push, every PR  
**Time:** ~3 min  
**Command:** `mvn clean verify`  
**Blocks:** Everything downstream

What runs:
- Maven unit tests across all 9 Java services
- Compilation and dependency validation

No Playwright tests run here — services are not up yet. If Stage 1 fails,
no further CI time is spent.

---

## Stage 2 — Docker Build Validation

**Trigger:** Every PR + every push to master  
**Time:** ~4 min  
**Depends on:** Stage 1  

What runs:
- `docker build` for all 10 services
- Validates Dockerfiles can produce an image

No tests run — this stage exists to catch broken Dockerfiles before
any test environment spins up and wastes runner time.

---

## Stage 3 — Smoke Tests (PR Required Gate)

**Trigger:** Every PR + every push to master  
**Time:** < 2 min test execution (~30s service startup overhead)  
**Depends on:** Stage 2  
**Tag:** `@smoke`  
**Blocks PR merge:** Yes (required check)

If smoke fails, no deeper testing runs. Fast feedback for developers.

### Test Coverage

#### `tests/auth.spec.ts` — Auth Service (6 tests)

| Test | What It Validates |
|------|-------------------|
| `valid login returns a JWT token` | Happy path: known credentials produce a valid JWT with correct role, username, and expiry |
| `invalid password returns 401` | Security boundary: wrong password must never produce a token |
| `missing fields returns 400` | Input validation at API boundary before auth logic is reached |
| `unknown user returns 401` | No user-existence leakage — unknown user and wrong password both return 401 |
| `validate endpoint accepts valid JWT` | Two-step flow: issued token is immediately usable for downstream service auth |
| `validate endpoint rejects invalid JWT` | Tampered or fabricated tokens are correctly rejected |

#### `tests/market-data.spec.ts` — Market Data Service (6 tests)

| Test | What It Validates |
|------|-------------------|
| `GET /market/prices returns all ticker prices` | Price feed is populated at startup; empty list would break all order submissions |
| `GET /market/prices/{ticker} returns price for AAPL` | Per-ticker lookup works; validates response shape consumed by order service |
| `GET /market/prices/{ticker} returns price for MSFT` | Multi-ticker support works; confirms price feed is not hard-coded to one symbol |
| `GET /market/status returns scenario engine status` | Surge-simulator is reachable; market open/closed state drives order acceptance |
| `POST /market/prices/{ticker}/tick simulates a price tick` | Price tick processing works; response includes updated ticker and price |
| `GET /market/prices/{ticker} returns 404 for unknown ticker` | Unknown tickers are rejected; prevents invalid orders from bypassing compliance |

---

## Stage 4 — Integration Tests (merge to master only)

**Trigger:** Push to master (post-merge) only  
**Time:** ~4 min  
**Depends on:** Stage 3  
**Tag:** `@integration`  
**Blocks:** Stage 5 (E2E)

Not run on every PR — keeps PR feedback fast. Unit tests + smoke already
cover fundamentals. Integration tests validate each service's full API
contract once code is merged.

### Test Coverage

#### `tests/orders.spec.ts` — Order Service (7 tests)

| Test | What It Validates |
|------|-------------------|
| `submit market BUY order for AAPL returns 201 with order shape` | Core submission path; validates gateway routing + order creation + response shape |
| `submit limit BUY order for MSFT includes limitPrice in response` | Limit orders store and return limitPrice; required for settlement price and order book |
| `limit order without limitPrice is rejected` | Input validation: incomplete limit orders are rejected before reaching matching engine |
| `GET /orders/book/{ticker} returns order book with bids and asks` | Order book endpoint returns both arrays (empty or populated) for frontend depth chart |
| `GET /orders returns paginated order list` | Pagination fields (content, totalElements) present for frontend infinite scroll |
| `BOT_OPERATOR role can read a specific order by ID` | RBAC: BOT_OPERATOR read access granted (EQ-140); cross-role read confirmed |
| `placed order reaches a terminal or active state within 10 seconds` | Order processing pipeline is functioning; perpetually PENDING = broken saga |

#### `tests/compliance.spec.ts` — Compliance Service (5 tests)

| Test | What It Validates |
|------|-------------------|
| `order with sufficient funds is approved` | Happy path: $100K cash, ~$1,895 order → approved with zero violations |
| `order with insufficient funds is rejected with INSUFFICIENT_FUNDS violation` | Critical rejection path: $178,200 order against $5,000 cash → INSUFFICIENT_FUNDS code returned |
| `compliance check response includes checkId for audit trail` | Every check produces a checkId linking the compliance decision to the audit log |
| `compliance history endpoint returns check records for a user` | History endpoint works; used for regulatory reporting and dispute resolution |
| `missing required fields returns 400` | Input validation: incomplete requests rejected before rule engine evaluates them |

#### `tests/ledger.spec.ts` — Ledger Service (6 tests)

| Test | What It Validates |
|------|-------------------|
| `GET /ledger/accounts/{userId} returns account for trader1` | Account lookup works; compliance and order service depend on this for fund checks |
| `account cash balance is non-negative` | Financial invariant: negative balance = accounting error in settlement |
| `GET /ledger/history/{userId} returns transaction list` | Transaction history endpoint works for account statements and reconciliation |
| `transaction history entries contain required fields` | Record shape valid: id, amount, type required for downstream consumers |
| `GET /ledger/positions/{userId} returns positions list` | Positions endpoint works; E2E saga test depends on this post-fill verification |
| `position entries contain required fields when present` | Position shape valid: ticker and quantity required for portfolio view and compliance |

#### `tests/audit.spec.ts` — Audit Service (6 tests)

| Test | What It Validates |
|------|-------------------|
| `GET /audit/events returns paginated audit events` | Primary event query works; pagination fields required for admin dashboard |
| `audit event records contain required fields` | Event shape valid: id, eventType, occurredAt required for timeline reconstruction |
| `GET /audit/events/user/{userId} returns events scoped to a trader` | Per-user filtering works; used for trader activity reports and dispute resolution |
| `GET /audit/events/type/{eventType} filters by event type` | Event-type filtering works; critical for incident triage |
| `GET /audit/events/order/{orderId} returns 200 or 404 for unknown order` | Per-order lookup handles missing orderId without 500; confirms graceful error handling |
| `audit event total count is non-decreasing between polls` | Append-only invariant enforced: audit events must never be deleted or overwritten |

---

## Stage 5 — E2E Saga Tests (merge to master only)

**Trigger:** Push to master, after Stage 4 passes  
**Time:** ~5 min  
**Depends on:** Stage 4  
**Tag:** `@e2e`

Highest-confidence signal in the pipeline. If the saga test passes, the full
order lifecycle is working end to end across all 5 services. These tests are
the slowest and most sensitive to infrastructure state, so they run last.

### Test Coverage

#### `tests/order-flow.spec.ts` — Cross-Service Order Lifecycle (5 tests)

| Test | What It Validates | Services Touched |
|------|-------------------|------------------|
| `full saga: submit order → compliance recorded → audit trail created → ledger updated` | Entire order lifecycle in sequence: auth token → order submit → poll for fill → verify audit event → verify compliance history → verify ledger balance decreased + position created | Auth, Gateway, Order, Compliance, Audit, Ledger |
| `unauthenticated request to gateway is rejected with 401` | Gateway auth middleware correctly blocks unauthenticated order submissions | Gateway |
| `BOT_OPERATOR role can read orders but not place them` | RBAC enforcement at gateway: read allowed, write forbidden for BOT_OPERATOR | Auth, Gateway |
| `sell order rejected when trader holds no position` | Gateway or order service rejects invalid sell (no position/bad ticker) before saga starts | Gateway, Order |
| `audit event count is non-decreasing across polls` | Append-only invariant holds even as new orders generate events concurrently | Order, Audit |

**Market-closed handling:** When the surge-simulator has closed the market,
order submissions return 409. Saga tests call `test.skip()` on 409 — this is
expected environment state, not a defect. CI shows the test as skipped, not failed.

---

## Nightly Regression (future / staging environment)

**Trigger:** Scheduled — 2:00 AM Pacific, Mon–Fri  
**Project:** `regression` (no tag filter — runs all 30 tests)  
**Environment:** Staging (real service URLs injected via GitHub secrets)

Full suite + JMeter performance run (`tests/performance/equiflow-load-test.jmx`).
Catches slow regressions and data-drift issues that don't surface in PR gates.

Failure behavior: Slack alert to `#equiflow-alerts`. Engineering team triages by 9 AM.

---

## Staging Environment Gate (future)

Before promoting from staging to production:

1. Full regression suite against staging URLs (all 30 tests green)
2. JMeter load test: p99 < 200ms at 100 rps per endpoint
3. Manual smoke of order placement flow
4. QA lead sign-off required

If any E2E test fails in staging: promotion is blocked. No exceptions without
an approved waiver documenting the known failure and its risk classification.

---

## Flake Management Policy

1. A test that passes on retry is **flaky** — log it immediately.
2. Flaky > 2 runs in a week: quarantine with `test.skip()` + open a ticket before re-enabling.
3. Flaky tests are never ignored. Green-on-retry is not green.
4. **Timing flakes:** Replace `setTimeout` with explicit polling or `waitForResponse()`.
5. **State leakage flakes:** Each suite manages its own `APIRequestContext` via `beforeAll`/`afterAll`.
6. **Infrastructure flakes:** Stage 3 retries twice in CI (`retries: 2`) to absorb startup jitter.

---

## Coverage Gaps (known, tracked)

| Gap | Priority | Notes |
|-----|----------|-------|
| Duplicate order detection | High | Saga orchestrator should reject identical orders within N seconds |
| Settlement service API | Medium | No direct tests for settlement flows — currently validated indirectly via ledger |
| Gateway rate limiting | Medium | Rate limit behavior under load is untested |
| WebSocket / streaming market data | Low | If market-data adds a WS feed, separate harness needed |
| Direct DB validation | Medium | Ledger and audit state verified via API only — no direct Postgres queries |
| Performance baseline tests | Medium | JMX file exists; needs CI integration on nightly schedule |
