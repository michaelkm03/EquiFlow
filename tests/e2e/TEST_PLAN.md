# EquiFlow — Test Plan & CI Stage Strategy

## Overview

This document defines what tests run at each stage of the CI/CD pipeline,
why they run there, and how failures are handled. The goal is fast feedback
on PRs without sacrificing release confidence on merge.

---

## Test Taxonomy

| Tag | Scope | Speed | Services Required |
|-----|-------|-------|-------------------|
| `@smoke` | Individual service health (auth, market-data) | < 60s | Auth + Market Data |
| `@integration` | API contract per service, no cross-service calls | 2–4 min | All services |
| `@e2e` | Full cross-service saga (order lifecycle) | 3–5 min | All services |
| _(none)_ | Full regression — all of the above | 8–12 min | All services |

---

## CI Stage Breakdown

### Stage 1 — Build & Unit Tests
**Trigger:** Every push, every PR  
**Time:** ~3 min  
**Command:** `mvn clean verify`  
**Blocks:** Everything downstream

What runs:
- Maven unit tests across all 9 Java services
- Compilation + dependency validation

What does NOT run:
- No Playwright tests — services aren't up yet
- No Docker builds

Failure behavior: PR is blocked. Author notified inline via GitHub Actions annotation.

---

### Stage 2 — Docker Build Validation
**Trigger:** Every PR + every push to master  
**Time:** ~4 min  
**Depends on:** Stage 1 passing  
**Blocks:** Smoke tests

What runs:
- `docker build` for all 10 services
- Validates Dockerfiles are syntactically correct and can produce an image
- Does NOT start services, does NOT run any tests

Failure behavior: Broken Dockerfiles caught before any test environment spins up.
Keeps the smoke stage from wasting runner time on a bad image.

---

### Stage 3 — Smoke Tests (PR Gate)
**Trigger:** Every PR + every push to master  
**Time:** < 2 min (test execution, after ~30s service startup)  
**Depends on:** Stage 2 passing  
**Tag filter:** `@smoke`  
**Blocks:** PR merge (required check)

What runs:
- `Auth Service` — login, invalid credentials, JWT validation (6 tests)
- `Market Data Service` — price reads, ticker lookup, price tick (6 tests)

What does NOT run:
- Order submission (stateful, slow)
- Cross-service flows
- Compliance, Ledger, Audit

Why here: These are the fastest, most foundational health checks. If auth is broken
or market data is down, there's no point running anything else. Keeping this stage
to 12 tests means PR feedback in under 2 minutes.

Flake strategy:
- `retries: 2` in CI — transient service startup timing issues get one retry
- If a test is red > 20% of runs, it's quarantined (`.skip`) and ticketed before re-enabling
- Traces captured on first retry, uploaded as artifacts

---

### Stage 4 — Integration Tests (merge to master)
**Trigger:** Push to master (post-merge) only  
**Time:** ~4 min  
**Depends on:** Stage 3 passing  
**Tag filter:** `@integration`  
**Blocks:** E2E stage

What runs:
- `Order Service` — order submission, order book, pagination, role-based access (7 tests)
- `Compliance Service` — approval, rejection, audit trail, history, validation (5 tests)
- `Ledger Service` — account balance, transaction history, positions (6 tests)
- `Audit Service` — event pagination, user events, type filter, append-only invariant (5 tests)

Why on merge, not PR: These tests require all 9 services running and take 4 minutes.
Running them on every PR would slow developer velocity without proportional value —
the smoke tests already catch broken fundamentals on PRs.

Failure behavior: Merge is flagged via commit status. On-call (or author) is notified.
No automatic rollback — requires human triage.

---

### Stage 5 — E2E Saga Tests (merge to master)
**Trigger:** Push to master, after integration tests pass  
**Time:** ~5 min  
**Depends on:** Stage 4 passing  
**Tag filter:** `@e2e`

What runs:
- `Order Lifecycle — Cross-Service E2E` (5 tests):
  - Full saga: Auth → Order Submit → Compliance recorded → Audit trail created → Ledger updated
  - Unauthenticated request rejected at gateway (401)
  - Role-based access: BOT_OPERATOR cannot place orders
  - Invalid sell order rejected (no position held)
  - Audit event count is non-decreasing (append-only invariant)

Why last: These tests depend on the entire service mesh being healthy and can take up
to 10s per test due to order polling. They provide the highest confidence signal —
if the saga test passes, the system is working end to end. They're also the most
brittle to infrastructure issues, so running them after integration tests reduces
false positives from partial startup.

Market-closed handling: Orders return 409 when the surge-simulator has closed the
market. Saga tests call `test.skip()` on 409 — this is expected environment state,
not a defect. CI will show the test as skipped, not failed.

---

### Nightly Regression (future / staging environment)
**Trigger:** Scheduled — 2:00 AM Pacific, Mon–Fri  
**Time:** 10–15 min  
**Project:** `regression` (no tag filter — runs everything)  
**Environment:** Staging (real URLs via secrets, not docker-compose)

What runs:
- Full suite: smoke + integration + e2e
- JMeter performance suite (`tests/performance/equiflow-load-test.jmx`)

Why nightly: Catches slow regressions that don't surface in fast PR gates —
data drift, latency degradation, intermittent cross-service bugs. Nightly cadence
gives a daily confidence signal without burning CI minutes on every commit.

Failure behavior:
- Slack alert to `#equiflow-alerts` channel
- Flaky test report attached (tests that passed on retry)
- Blocking on nightly failures is optional — engineering team triages by 9 AM

---

## Staging Environment Gate (future)

Before promoting a release from staging to production:

1. Run full regression suite against staging URLs
2. Run JMeter load test (target: p99 < 200ms at 100 rps per endpoint)
3. Manual smoke check of order placement UI (if frontend is in scope)
4. Sign-off required from one QA lead

If any E2E test fails in staging: promotion is blocked. No exceptions without
an approved waiver documenting the known failure and its risk.

---

## How to Run Locally

```bash
# Start all services
docker compose up -d

# Install dependencies (first time)
cd tests/e2e && npm ci && npx playwright install chromium

# Smoke tests only (fast check)
npx playwright test --project=smoke

# Integration tests
npx playwright test --project=integration

# E2E cross-service saga
npx playwright test --project=e2e

# Full regression suite
npx playwright test --project=regression

# Single test file
npx playwright test tests/order-flow.spec.ts

# Debug a failure (headed mode + Playwright inspector)
npx playwright test tests/order-flow.spec.ts --debug

# View HTML report
npx playwright show-report
```

---

## Flake Management Policy

1. A test that fails on initial run but passes on retry is **flaky** — log it.
2. If a test is flaky > 2 runs in a week: quarantine with `test.skip()` + open a ticket.
3. Flaky tests are never ignored — they're fixed or removed. A green-on-retry is not green.
4. Timing-based flakes: use `page.waitForResponse()` or explicit polling instead of `setTimeout`.
5. State leakage flakes: each test suite uses `beforeAll`/`afterAll` to own its context lifecycle.

---

## Coverage Gaps (known, tracked)

| Gap | Priority | Notes |
|-----|----------|-------|
| Duplicate order detection | High | Saga orchestrator should reject identical orders within N seconds |
| Settlement service validation | Medium | Currently no tests for settlement flows |
| Rate limiting | Medium | Gateway rate limit behavior untested |
| WebSocket / streaming market data | Low | If market data adds a WS feed, needs separate harness |
| Postgres data validation | Medium | Ledger and audit DB state validated via API only, not direct DB query |
