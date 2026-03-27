# EquiFlow — Test Platform Plan
**Role:** Senior Software Engineer — Test Platform
**Status:** EQ-113a complete — COMPENSATING checkpoint in place | **Last Updated:** 2026-03-27

> **Key facts verified in source before planning:**
> - `failSaga()` now saves `COMPENSATING` to DB before resolving to `FAILED` — crash-safe checkpoint for EQ-113c recovery job (EQ-113a ✅)
> - `OrderClient` has only `triggerMatch()` — no `cancelOrder()` method exists (added by EQ-113b)
> - `OrderType.STOP_LOSS` fully implemented — `PENDING_TRIGGER`/`TRIGGERED` statuses, `trigger_price` column, `StopLossService` trigger evaluation, `equiflow.order.stop-loss.triggered` Kafka topic, saga short-circuit and re-entry all live
> - `ComplianceResult` DTO has no enriched fields — `complianceReason`, `blockedUntil`, `triggerOrderId` don't exist yet
> - `ledger-service` has no test directory — it is the only service with zero tests

---

## Test Case Reference

A full flat breakdown of every test case in this plan is available in
[`TEST_CASES.csv`](TEST_CASES.csv).

**Columns:** `Item` · `Test Class` · `Test Case` · `Type` · `Test Layer` · `Prereqs` · `Happy/Edge` · `JD Mapping` · `Setup` · `Assert`

Use this file to:
- Filter by `Happy/Edge` to review coverage balance per item
- Filter by `JD Mapping` to see which JD requirement each test satisfies
- Filter by `Prereqs` to identify which tests can be written immediately vs which require feature work first
- Track implementation status by adding a `Status` column as work progresses

---

## Table of Contents

⚪ Not Started &nbsp;|&nbsp; 🔵 In Progress &nbsp;|&nbsp; ✅ Done

| # | Status | Requirement | Planned Work | Type | Prereqs | Priority |
|---|--------|-----------------|--------------|------|---------|----------|
| 1 | 🔵 | [Saga Compensation](#1-saga-compensation) | `SagaCompensationIntegrationTest` | Integration Test | EQ-113a ✅ — EQ-113b, EQ-113c remaining | P0 |
| 2 | 🔵 | [Ledger Concurrency](#2-ledger-concurrency) | `LedgerServiceTest` + `LedgerServiceConcurrencyTest` | Unit + Integration Test | None | P0 |
| 3 | ⚪ | [Stop-Loss Order Testing](#3-stop-loss-order-testing) | `StopLossOrderServiceTest` | Unit Test | None — EQ-101 complete | P0 |
| 4 | ⚪ | [API Mocking / Contract Testing](#4-api-mocking--contract-testing) | `PortfolioSummaryContractTest` | Contract Test | Implement EQ-103 portfolio endpoint | P0 |
| 5 | ⚪ | [E2E — Full Trade Lifecycle](#5-e2e--full-trade-lifecycle) | `trading-lifecycle.spec.ts` | E2E Test | None | P0 |
| 6 | ✅ | [CI/CD Pipeline](#6-cicd-pipeline) | `.github/workflows/ci.yml` | Infrastructure | None | P0 |
| — | ⚪ | [Infrastructure Changes](#infrastructure-changes) | WireMock, Testcontainers, CI profiles | Infra | None | P0 |

---

## Implementation Order

| Order | Item | Reason |
|-------|------|--------|
| 1 | ~~Item 2 — `LedgerServiceTest`~~ | ✅ 11 unit tests written — `LedgerServiceConcurrencyTest` (Testcontainers) still remaining |
| 2 | Item 3 — `StopLossOrderServiceTest` | EQ-101 now complete — no prereqs remaining; write tests immediately |
| 3 | Item 1 — Saga compensation | EQ-113a done; EQ-113b + EQ-113c next — highest interview impact |
| 4 | Item 5 — `trading-lifecycle.spec.ts` | No prereqs; first test to cross service boundaries |
| 5 | ~~Item 6 — CI pipeline~~ | ✅ Done |
| 6 | EQ-103 + Item 4 — Portfolio contract test | WireMock differentiator; requires EQ-103 feature first |

---

## Planned Work

---

### 1. Saga Compensation

**JD Requirement:** *"Strong understanding of distributed systems architecture... Architect and implement solutions that accelerate integration and chaos testing."*

**Gap:** If the saga fails after placing a ledger hold, the user's funds are permanently frozen. `LedgerClient.release()` is implemented but not yet called from `failSaga()`.

**Progress:**
- ✅ **EQ-113a** — `failSaga()` saves `COMPENSATING` to DB before resolving to `FAILED`. Crash-safe boundary in place; `SagaRecoveryJob` can query this status to resume interrupted compensation flows. Unit test: `SagaCompensationTest#failSaga_setsCompensatingBeforeFeign`.
- ⚪ **EQ-113b** — Add `POST /orders/{orderId}/system-cancel` to `order-service`; add `release()` idempotency to `ledger-service`. Prerequisite for EQ-113c.
- ⚪ **EQ-113c** — Wire `cancelOrder()` + `release()` Feign calls into `failSaga()`; add `SagaRecoveryJob`. Depends on EQ-113a + EQ-113b.

**Planned: `SagaCompensationIntegrationTest`**
`saga-orchestrator/src/test/java/com/equiflow/saga/SagaCompensationIntegrationTest.java`
Uses **Testcontainers** (real Postgres + real Kafka). H2 does not enforce `SELECT FOR UPDATE` — compensation correctness requires real Postgres.

| Test Case | Setup | Assert |
|-----------|-------|--------|
| `happyPath_allStepsComplete` | All 5 steps succeed | Saga = `COMPLETED`; hold converted to debit; `availableCash` reduced by fill value |
| `failAtSettlement_holdIsReleased` | Steps 1–4 pass; `settlementClient.createSettlement()` throws | `ledgerClient.release()` called; `availableCash` fully restored to pre-order value |
| `failAtMatching_noHoldPlaced` | Compliance passes; `orderClient.triggerMatch()` throws | No hold placed; `availableCash` unchanged; saga = `FAILED` |
| `compensationIsIdempotent` | `failSaga()` called twice on same saga | `availableCash` not double-released; second call is a no-op; no exception thrown |
| `compensationItself_fails` | `ledgerClient.release()` throws during compensation | Saga status = `FAILED`; compensation error logged explicitly — no silent swallow; `failureReason` includes compensation step detail |
| `failAtCompliance_noSideEffects` | Compliance rejects (step 1 fails) | No hold placed; no order created; `availableCash` unchanged (distinct from steps 2–5 failures) |

**Example — what the test verifies:**
```
Account starts: availableCash = $100,000
Saga step 4 runs: hold placed → availableCash = $98,500
Saga step 5 throws: settlement-service down
compensate() fires: release hold → availableCash = $100,000  ✅
Without compensation: availableCash stays $98,500 forever  ❌
```

---

### 2. Ledger Concurrency

**JD Requirement:** *"Proven programming skills in developing enterprise scale systems."*

**Gap:** `LedgerService` uses `SELECT FOR UPDATE` to prevent double-spend, but **no test directory exists** for `ledger-service`. If the lock is removed during a refactor, nothing catches it.

**Prerequisite:** None. `hold()`, `release()`, `debit()` are all implemented.

**Planned: `LedgerServiceTest`** (unit, Mockito)
`ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `hold_reducesAvailableBalance` | `hold($1,500)` on $10,000 account | `availableCash` = $8,500; `heldCash` = $1,500 |
| `release_restoresAvailableBalance` | `hold($1,500)` then `release($1,500)` | `availableCash` = $10,000; `heldCash` = 0 |
| `debit_reducesBalanceAndReleasesHold` | `hold($1,500)` then `debit($1,500)` | `availableCash` = $8,500; `heldCash` = 0 |
| `hold_insufficientFunds_throwsException` | `hold($15,000)` on $10,000 account | `InsufficientFundsException` thrown; balance unchanged |
| `hold_exactAvailableBalance_succeeds` | `hold($10,000)` on $10,000 account (boundary) | Hold succeeds; `availableCash` = 0; `heldCash` = $10,000 |
| `release_noExistingHold_isIdempotent` | `release()` called with no active hold | No exception; balance unchanged — safe to call during saga compensation |

**Planned: `LedgerServiceConcurrencyTest`** (integration, Testcontainers Postgres)
`ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceConcurrencyTest.java`

| Test Case | Setup | Assert |
|-----------|-------|--------|
| `concurrentHolds_onlyOneFills` | `CountDownLatch` releases two threads simultaneously; each requests $800 hold on $1,000 account | Exactly one succeeds; one throws `InsufficientFundsException`; final `availableCash` = $200 |
| `concurrentDebits_balanceIsConsistent` | Two threads debit $500 simultaneously on $1,000 account | Final balance = $500; no lost update; no negative balance |

**Why `CountDownLatch`:** Both threads call `latch.await()` before executing — they start at the same instant, maximizing the chance of a race. Without this, threads run sequentially and the lock is never contested.

---

### 3. Stop-Loss Order Testing

**JD Requirement:** *"Expertise in automated testing strategies... Design, develop, and maintain software that enables engineers to test backend applications."*

**Status:** EQ-101 is fully implemented. All prerequisites are met — tests can be written immediately.

**What was implemented (EQ-101):**
- `PENDING_TRIGGER` and `TRIGGERED` statuses; `@PrePersist` resolves initial status by order type
- `trigger_price NUMERIC(18,4)` column via Flyway migration; composite index on `(ticker, status, trigger_price)`
- `StopLossService.evaluateTriggers()` — queries `PENDING_TRIGGER` orders where `triggerPrice >= currentPrice`, marks `TRIGGERED`, publishes Kafka event
- `equiflow.order.stop-loss.triggered` Kafka topic; `SagaEventListener.onStopLossTriggered` re-enters saga with `type` overridden to `MARKET`
- `POST /orders/internal/stop-loss/evaluate` — internal endpoint called by `market-data-service` on every price tick and scenario step
- `OrderSaga` short-circuits after compliance for `STOP_LOSS` type; full execution resumes on trigger

**Planned: `StopLossOrderServiceTest`**
`order-service/src/test/java/com/equiflow/order/StopLossOrderServiceTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `submitStopLoss_statusIsPendingTrigger` | `POST /orders` with `type: STOP_LOSS, triggerPrice: 150.00` | Status = `PENDING_TRIGGER`; order not sent to matching engine |
| `submitStopLoss_missingTriggerPrice_returns400` | `POST /orders` with `type: STOP_LOSS`, no `triggerPrice` | HTTP 400; message = `"Trigger price is required for STOP_LOSS orders"` |
| `priceBelowTrigger_orderTriggered` | `evaluateTriggers("AAPL", 149.99)` against order with `triggerPrice: 150.00` | Status = `TRIGGERED`; `order.stop-loss.triggered` Kafka event published |
| `priceAtTrigger_orderTriggered` | `evaluateTriggers("AAPL", 150.00)` — boundary, inclusive check | Status = `TRIGGERED` |
| `priceAboveTrigger_orderNotTriggered` | `evaluateTriggers("AAPL", 150.01)` | Status unchanged = `PENDING_TRIGGER`; no Kafka event published |
| `cancelledOrder_neverTriggers` | Order cancelled via `DELETE /orders/{id}`; then `evaluateTriggers` called | Query skips `CANCELLED` orders; status unchanged; no event published |
| `twoStopLossOrders_bothTriggerIndependently` | Two orders on AAPL, triggers `$150` and `$145`; `evaluateTriggers("AAPL", 144.00)` | Both transition to `TRIGGERED`; each publishes its own Kafka event; no cross-order state |
| `priceOscillatesAcrossTrigger_firesOnce` | Order triggered → status = `TRIGGERED`; second `evaluateTriggers` call with same price | Second call returns empty — `TRIGGERED` orders excluded from query; no duplicate event |
| `sagaShortCircuits_forStopLossType` | Saga receives `ORDER_PLACED` with `type: STOP_LOSS` | Saga reaches step 5 (COMPLETED) after compliance only; steps 2–4 not executed |
| `sagaReEnters_afterTrigger` | Saga receives `STOP_LOSS_TRIGGERED`; `type` overridden to `MARKET` | Saga executes all 5 steps including ORDER_MATCHING; order reaches `FILLED` or `REJECTED` |

---

### 4. API Mocking / Contract Testing

**JD Requirement:** *"Expertise in automated testing strategies, testing in production, test tenancy, **API mocking, traffic capture, routing and playback technologies**."*

**Gap:** No WireMock, MockServer, or contract tests anywhere in the project. All unit tests use in-process Mockito only. This is the single JD requirement with zero coverage.

**Prerequisite (EQ-103):** `GET /portfolio/summary` on `ledger-service` does not exist. Implement it before writing the contract test.

**Planned: `PortfolioSummaryContractTest`**
`ledger-service/src/test/java/com/equiflow/ledger/PortfolioSummaryContractTest.java`

Uses **WireMock** to stub `market-data-service` at the HTTP wire level — not a Mockito interface mock. WireMock proves `ledger-service` makes the correct HTTP request to the correct URL with the correct headers. Mockito cannot verify this.

| Test Case | WireMock Stub | Assert |
|-----------|--------------|--------|
| `portfolioSummary_calculatesCorrectPnl` | `GET /market-data/prices/AAPL` → `{ "price": 155.00 }` | P&L = `(155.00 - 150.00) × 10 shares = $50.00 unrealized gain` |
| `portfolioSummary_marketDataDown_returns503` | `GET /market-data/prices/*` → HTTP 503 | Portfolio returns HTTP 503; no stale data silently returned |
| `portfolioSummary_unknownTicker_excludedWithError` | `GET /market-data/prices/UNKNWN` → HTTP 404 | Position excluded from totals; present in `pricingErrors: ["UNKNWN"]` |
| `portfolioSummary_noPositions_returns200` | No stub needed | HTTP 200; `positions: []`; `totalMarketValue: 0`, `totalUnrealizedPnl: 0` |
| `portfolioSummary_partialPricingFailure_partialResult` | AAPL stub returns 200; TSLA stub returns 503 | AAPL position included with correct P&L; TSLA in `pricingErrors`; aggregate totals reflect AAPL only |
| `portfolioSummary_marketDataTimeout_returns503` | `GET /market-data/prices/*` → delayed 6,000ms (beyond Feign timeout) | Portfolio returns HTTP 503; no hung thread; response within 5,100ms |

**Example — WireMock setup vs Mockito:**
```java
// WireMock (HTTP wire — proves the real HTTP call is made)
stubFor(get("/market-data/prices/AAPL")
    .willReturn(okJson("{\"price\": 155.00}")));

// vs Mockito (in-process only — does not verify HTTP behaviour)
when(marketDataClient.getPrice("AAPL")).thenReturn(155.00);
```

---

### 5. E2E — Full Trade Lifecycle

**JD Requirement:** *"Deliver software that enables seamless testing of backend systems in cloud-native, containerized, and CI/CD environments, supporting shift-left and continuous delivery."*

**Gap:** `orders.spec.ts` submits an order and polls for a terminal status. No E2E test verifies what happens after a fill — ledger balance, settlement record, or audit log. The full system is never asserted end-to-end.

**Prerequisite:** None. Orders, settlement, audit, and ledger endpoints are all live.

**Planned: `trading-lifecycle.spec.ts`**
`tests/e2e/tests/trading-lifecycle.spec.ts`

| Test Case | Steps | Final Assert |
|-----------|-------|-------------|
| `fullTradeCycle_balanceReducedAfterFill` | Record pre-trade balance → submit BUY 10 AAPL → poll `FILLED` → `GET /ledger/accounts/{userId}` | `availableCash` reduced by `fillPrice × 10` |
| `fullTradeCycle_settlementCreated` | Submit BUY → poll `FILLED` → `GET /settlements?orderId={id}` | Settlement record exists; `settlementDate` = next NYSE business day |
| `fullTradeCycle_auditEventLogged` | Submit BUY → poll `FILLED` → `GET /audit/events?orderId={id}` | ≥ 1 audit event with matching `orderId` |
| `washSaleBlock_violationCodePresent` | Sell AAPL at a loss → immediately submit BUY AAPL → check response | HTTP 200; `approved: false`; `violations[0].code = "WASH_SALE"` |
| `sellOrder_reducesPosition` | BUY 10 AAPL → poll `FILLED` → SELL 10 AAPL → poll `FILLED` → `GET /ledger/positions/{userId}` | AAPL position quantity = 0 or removed from positions |
| `orderWhileMarketClosed_returns409` | Submit `MARKET` order outside NYSE hours (9:30–16:00 ET) | HTTP 409 with message `"Market is closed"`; no saga started; no hold placed |

---

### 6. CI/CD Pipeline

**JD Requirement:** *"Deliver software in CI/CD environments supporting shift-left and continuous delivery. Familiarity with CI/CD pipelines (e.g., Argo, GitLab CI/CD)."*

**Status:** Implemented. `.github/workflows/ci.yml` runs `mvn clean verify` on every push and validates all Docker builds on PRs to master.

**Prerequisite:** None.

**Planned: `.github/workflows/ci.yml`**

| Stage | Command | Fails PR If |
|-------|---------|------------|
| `build` | `mvn --batch-mode verify -DskipTests` | Compilation error in any module |
| `unit-tests` | `mvn --batch-mode test` | Any unit test fails |
| `integration-tests` | `mvn --batch-mode verify` with Testcontainers | Any integration test fails |
| `e2e-tests` | `docker-compose -f docker-compose.test.yml up -d` → `npx playwright test` | Any E2E test fails |
| `allure-report` | `allure generate` → publish to GitHub Pages | Never blocks; informational only |

Triggers: `push` and `pull_request` to `master`. README displays live CI badge.

**Note:** `docker-compose.test.yml` is a lean profile — runs only services needed for E2E, omits surge-simulator, uses faster startup config. Reduces CI spin-up time.

---

## Infrastructure Changes

These changes are required to support the test work above. None involves modifying production application logic.

| Change | File | Purpose | Requirement |
|--------|------|---------|-----------------|
| Add WireMock container | `docker-compose.yml` | Stub external HTTP services at container level for local + CI testing | API mocking / test tenancy |
| Add Testcontainers to `ledger-service` pom | `ledger-service/pom.xml` | Real Postgres required for `SELECT FOR UPDATE` concurrency tests — H2 does not enforce the same locking semantics | Distributed systems testing |
| Add Testcontainers to `saga-orchestrator` pom | `saga-orchestrator/pom.xml` | Real Kafka required for saga compensation integration tests — embedded Kafka drops messages under concurrent load | Distributed systems testing |
| Add `docker-compose.test.yml` | project root | Lean CI stack — omits surge-simulator; faster startup; prevents port conflicts between parallel test runs | Shift-left / CI/CD |
| Isolated Kafka consumer groups for tests | `application-test.yml` per service | Prevents test runs from consuming each other's topic offsets; each test run gets a unique consumer group ID | Automated testing in distributed systems |
| Wire Allure to GitHub Actions | `.github/workflows/ci.yml` | Visual test report published on every PR; standard for FinTech platform teams | CI/CD, test maturity |

---

## JD Coverage Summary

| JD Requirement | Covered By |
|----------------|-----------|
| Distributed systems architecture | Items 1 (saga compensation), 2 (concurrency) |
| Automated testing strategies at scale | Items 2, 3, 4, 5 |
| API mocking, test tenancy, traffic routing | Item 4 (WireMock) + infrastructure |
| CI/CD, shift-left, continuous delivery | Item 6 (GitHub Actions) + infrastructure |
| Java, Kotlin, Go, Python | Java/Kotlin in codebase; Go in Tala experience |
| AWS, Docker, Kubernetes | `docker-compose.yml`; AWS referenced in Altruist resume bullets |
