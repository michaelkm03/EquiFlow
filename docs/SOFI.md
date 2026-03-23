# EquiFlow — SoFi Test Platform Alignment Plan
**Role:** Senior Software Engineer — Test Platform | **Req ID:** 7507199003
**Status:** Planning — No code written yet | **Last Updated:** 2026-03-22

> **Key facts verified in source before planning:**
> - `failSaga()` sets status to `FAILED` and saves — compensation never fires; `LedgerClient.release()` exists but is never called
> - `OrderClient` has only `triggerMatch()` — no `cancelOrder()` method exists
> - `OrderType.STOP_LOSS` enum exists — zero service logic handles it
> - `ComplianceResult` DTO has no enriched fields — `complianceReason`, `blockedUntil`, `triggerOrderId` don't exist yet
> - `ledger-service` has no test directory — it is the only service with zero tests

---

## Test Case Reference

A full flat breakdown of every test case in this plan is available in
[`SOFI_TEST_CASES.csv`](SOFI_TEST_CASES.csv).

**Columns:** `Item` · `Test Class` · `Test Case` · `Type` · `Test Layer` · `Prereqs` · `Happy/Edge` · `SoFi JD Mapping` · `Setup` · `Assert`

Use this file to:
- Filter by `Happy/Edge` to review coverage balance per item
- Filter by `SoFi JD Mapping` to see which JD requirement each test satisfies
- Filter by `Prereqs` to identify which tests can be written immediately vs which require feature work first
- Track implementation status by adding a `Status` column as work progresses

---

## Table of Contents

⚪ Not Started &nbsp;|&nbsp; 🔵 In Progress &nbsp;|&nbsp; ✅ Done

| # | Status | SoFi Requirement | Planned Work | Type | Prereqs | Priority |
|---|--------|-----------------|--------------|------|---------|----------|
| 1 | ⚪ | [Saga Compensation](#1-saga-compensation) | `SagaCompensationIntegrationTest` | Integration Test | Add `cancelOrder()` to `OrderClient` | P0 |
| 2 | ⚪ | [Ledger Concurrency](#2-ledger-concurrency) | `LedgerServiceTest` + `LedgerServiceConcurrencyTest` | Unit + Integration Test | None | P0 |
| 3 | ⚪ | [Stop-Loss Order Testing](#3-stop-loss-order-testing) | `StopLossOrderServiceTest` | Unit Test | Implement EQ-101 trigger logic | P0 |
| 4 | ⚪ | [API Mocking / Contract Testing](#4-api-mocking--contract-testing) | `PortfolioSummaryContractTest` | Contract Test | Implement EQ-103 portfolio endpoint | P0 |
| 5 | ⚪ | [E2E — Full Trade Lifecycle](#5-e2e--full-trade-lifecycle) | `trading-lifecycle.spec.ts` | E2E Test | None | P0 |
| 6 | ⚪ | [E2E — Stop-Loss Lifecycle](#6-e2e--stop-loss-lifecycle) | `stop-loss-lifecycle.spec.ts` | E2E Test | EQ-101 | P1 |
| 7 | ⚪ | [Chaos / Failure Injection](#7-chaos--failure-injection) | `chaos-recovery.spec.ts` | E2E + Chaos Test | None | P1 |
| 8 | ⚪ | [CI/CD Pipeline](#8-cicd-pipeline) | `.github/workflows/ci.yml` | Infrastructure | None | P0 |
| 9 | ⚪ | [AI-Driven Test Generation](#9-ai-driven-test-generation) | `tools/ai-test-generator/` | AI Tool | None | P1 |
| 10 | ⚪ | [Load / Performance — Expanded](#10-load--performance--expanded) | Stop-loss + portfolio JMeter suites | Performance Test | EQ-101, EQ-103 | P2 |
| 11 | ⚪ | [Compliance Enriched Response](#11-compliance-enriched-response) | `WashSaleEnrichedResponseTest` | Unit + E2E Test | Add fields to `ComplianceResult` (EQ-203) | P1 |
| — | ⚪ | [Infrastructure Changes](#infrastructure-changes) | WireMock, Testcontainers, CI profiles | Infra | None | P0 |

---

## Implementation Order

| Order | Item | Reason |
|-------|------|--------|
| 1 | Item 2 — `LedgerServiceTest` | No prereqs; only service with zero tests; unblocks item 1 |
| 2 | Item 1 — Saga compensation | Highest interview impact; fixes critical production gap |
| 3 | Item 5 — `trading-lifecycle.spec.ts` | No prereqs; first test to cross service boundaries |
| 4 | Item 7 — `chaos-recovery.spec.ts` | No prereqs; surge-simulator fully built |
| 5 | Item 8 — CI pipeline | No prereqs; makes repo look production-ready immediately |
| 6 | EQ-101 + Item 3 — Stop-loss | Large feature; prereq for items 6 and 10 |
| 7 | EQ-103 + Item 4 — Portfolio contract test | WireMock differentiator |
| 8 | Item 9 — AI test generator | Biggest differentiator; do after core tests are solid |
| 9 | Items 10, 11 | Depend on EQ-101, EQ-103, EQ-203 |

---

## Planned Work

---

### 1. Saga Compensation

**SoFi JD:** *"Strong understanding of distributed systems architecture... Architect and implement solutions that accelerate integration and chaos testing."*

**Gap:** `failSaga()` marks the saga `FAILED` and saves — that's it. If the saga fails after placing a ledger hold (step 4 passes, step 5 throws), the user's funds are permanently frozen. `LedgerClient.release()` is already implemented but never called.

**Prerequisite:** Add `cancelOrder()` to `OrderClient` Feign interface + a `POST /orders/{id}/cancel` endpoint to `order-service`. Without it, compensation at the matching step cannot be implemented or tested.

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

**SoFi JD:** *"Proven programming skills in developing enterprise scale systems."*

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

**SoFi JD:** *"Expertise in automated testing strategies... Design, develop, and maintain software that enables engineers to test backend applications."*

**Gap:** `OrderType.STOP_LOSS` enum exists. Nothing else does — no `PENDING_TRIGGER` status, no trigger evaluation logic, no Kafka topic, no tests.

**Prerequisite (EQ-101):** Before tests can be written:
- Add `TRIGGERED` to `OrderStatus` enum (`PENDING_TRIGGER` already exists)
- Add `trigger_price NUMERIC(18,4)` column via Flyway migration
- Implement trigger evaluation in `order-service` — on each price tick from `market-data-service`, query open stop-loss orders for that ticker and fire when `marketPrice <= triggerPrice`
- Add `order.stop-loss.triggered` Kafka topic

**Planned: `StopLossOrderServiceTest`**
`order-service/src/test/java/com/equiflow/order/StopLossOrderServiceTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `submitStopLoss_statusIsPendingTrigger` | `POST /orders` with `type: STOP_LOSS, triggerPrice: 150.00` | Status = `PENDING_TRIGGER`; order not sent to matching engine |
| `priceBelowTrigger_orderTriggered` | Price tick: AAPL = `149.99` (trigger = `150.00`) | Status = `TRIGGERED`; `order.stop-loss.triggered` Kafka event published |
| `priceAtTrigger_orderTriggered` | Price tick: AAPL = `150.00` exactly | Status = `TRIGGERED` — boundary condition, inclusive check |
| `priceAboveTrigger_orderNotTriggered` | Price tick: AAPL = `150.01` | Status unchanged = `PENDING_TRIGGER`; no Kafka event |
| `cancelledOrder_neverTriggers` | Order cancelled via `DELETE /orders/{id}`; then price drops | Evaluation skipped; status stays `CANCELLED` |
| `marketClosed_triggerQueues` | Price hits trigger at 6 PM ET (market closed) | Order queued; not converted to market order; executes at next open |
| `twoStopLossOrders_bothTriggerIndependently` | Two orders, AAPL, triggers `$150` and `$145`; price drops to `$144` | Both trigger independently; no cross-order state contamination |
| `triggerPriceAboveMarket_accepted` | Submit stop-loss with `triggerPrice: $200` while AAPL = `$150` | Order accepted and stored as `PENDING_TRIGGER`; warning not required |
| `priceOscillatesAcrossTrigger_firesOnce` | Price drops below trigger, recovers, drops again within same tick cycle | Alert fires exactly once; re-evaluation of `TRIGGERED` orders skipped |

---

### 4. API Mocking / Contract Testing

**SoFi JD:** *"Expertise in automated testing strategies, testing in production, test tenancy, **API mocking, traffic capture, routing and playback technologies**."*

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

**SoFi JD:** *"Deliver software that enables seamless testing of backend systems in cloud-native, containerized, and CI/CD environments, supporting shift-left and continuous delivery."*

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

### 6. E2E — Stop-Loss Lifecycle

**SoFi JD:** *"Experience developing in a cloud environment... E2E testing."*

**Gap:** No stop-loss tests exist at any layer.

**Prerequisite:** EQ-101 implemented (see item 3).

**Planned: `stop-loss-lifecycle.spec.ts`**
`tests/e2e/tests/stop-loss-lifecycle.spec.ts`

| Test Case | Steps | Final Assert |
|-----------|-------|-------------|
| `stopLoss_triggersOnPriceDrop` | Submit stop-loss `triggerPrice: 150.00` → call `POST /market/prices/AAPL/tick` repeatedly until price ≤ 150.00 → poll order | Status transitions: `PENDING_TRIGGER` → `TRIGGERED` → `FILLED` |
| `stopLoss_cancelBeforeTrigger` | Submit stop-loss → `DELETE /orders/{id}` → call tick endpoint repeatedly | Status stays `CANCELLED`; no fill; balance unchanged |
| `stopLoss_historyIncludesBothPrices` | Submit and trigger stop-loss → `GET /orders/{id}` | Response includes `triggerPrice: 150.00` and `fillPrice: <market price at execution>` |
| `twoStopLoss_sameTickerBothTrigger` | Submit two stop-loss orders on AAPL at `$150` and `$145` → call tick endpoint until price ≤ $144 | Both orders triggered and filled independently; no shared state interference |

> **Note:** There is no direct price-set endpoint — `POST /market/prices/{ticker}/tick` simulates a random tick. Stop-loss E2E tests either loop ticks until the trigger condition is met, or a new `POST /admin/market/prices/{ticker}/set` endpoint should be added as part of EQ-101 to enable deterministic testing.

---

### 7. Chaos / Failure Injection

**SoFi JD:** *"Architect and implement solutions that accelerate chaos testing... Experience with failure injection and chaos testing (Gremlin, AWS FIS)."*

**Gap:** `surge-simulator` exposes a full chaos injection API. No test exercises it. The entire service is untested production code.

**Prerequisite:** None. `POST /admin/chaos/start`, `GET /admin/chaos/status`, `POST /admin/chaos/stop` are all live.

**Planned: `chaos-recovery.spec.ts`**
`tests/e2e/tests/chaos-recovery.spec.ts`

This is the EquiFlow equivalent of Gremlin / AWS FIS testing.

| Test Case | Chaos Config | Assert |
|-----------|-------------|--------|
| `dbFailure_sagasFailGracefully` | `{ mode: "DB", failureRatePercent: 50 }` → submit 10 orders concurrently | Every order is either `FILLED` or `FAILED` with a `failureReason`; no order stuck in `PENDING` |
| `networkLatency_ordersEventuallyFill` | `{ mode: "LATENCY", latencyMs: 2000 }` → submit 1 order | Order reaches terminal state within 30s; no unhandled 5xx to client |
| `chaosStop_systemRecoversCleanly` | Start chaos → stop chaos → submit fresh order | Fresh order fills with normal latency; chaos session status = `STOPPED` |
| `highFailureRate_noNegativeBalances` | `{ failureRatePercent: 80 }` → 20 concurrent orders | All `availableCash` values ≥ 0 after chaos; no account stuck with `heldCash > availableCash` |
| `chaosDuringSettlementBatch` | Create pending settlement records → start DB failure chaos → call `POST /settlement/admin/run` to trigger batch manually | Settlement batch either completes all pending settlements or logs each failure explicitly; no silent data loss; endpoint verified against real endpoint in source |
| `kafkaLatency_eventsDeliveredEventually` | `{ mode: "LATENCY", latencyMs: 3000 }` → submit order → audit log checked after 15s | Audit event eventually present; Kafka consumer lag resolves; no dropped messages |

**Example chaos start request:**
```bash
POST /admin/chaos/start
{ "mode": "BOTH", "latencyMs": 2000, "failureRatePercent": 30, "triggeredBy": "bot-operator1" }
```

---

### 8. CI/CD Pipeline

**SoFi JD:** *"Deliver software in CI/CD environments supporting shift-left and continuous delivery. Familiarity with CI/CD pipelines (e.g., Argo, GitLab CI/CD)."*

**Gap:** No CI pipeline exists. Tests must be run manually.

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

### 9. AI-Driven Test Generation

**SoFi JD:** *"Provide technical leadership with a focus on integrating AI-driven automation... Research, prototype, and productionize AI/ML tools to enhance developer productivity, test coverage, and test maturity."*

**Gap:** No AI tooling in the project.

**Prerequisite:** None.

**Planned: `tools/ai-test-generator/`**

```
tools/
└── ai-test-generator/
    ├── generate_tests.py   # CLI — reads OpenAPI spec, writes .java test files
    ├── spec_parser.py      # Parses openapi.yaml; extracts endpoints + request/response schemas
    ├── claude_client.py    # Sends each endpoint to Claude API with a structured prompt
    ├── test_writer.py      # Writes generated skeletons into the correct service test directory
    ├── requirements.txt    # anthropic, pyyaml
    └── README.md
```

**Example invocation:**
```bash
python generate_tests.py --service order-service --output order-service/src/test/java/
```

**What it generates per endpoint:**
- Happy path test with a valid request body matching the OpenAPI schema
- 3 edge case tests: invalid input, missing auth header, boundary value
- Mockito stubs for all `@Autowired` service dependencies
- TestNG `@Test` annotations with descriptive method names

**Example output for `POST /orders`:**
```java
@Test(description = "POST /orders — happy path: market BUY order fills successfully")
public void postOrders_marketBuy_returns201() { ... }

@Test(description = "POST /orders — missing ticker field returns HTTP 400")
public void postOrders_missingTicker_returns400() { ... }

@Test(description = "POST /orders — unauthenticated request returns HTTP 401")
public void postOrders_noAuthHeader_returns401() { ... }
```

**Why this matters:** Directly implements *"AI for automated test generation"* — the most differentiating requirement on the JD. Combined with the 90% test creation time reduction metric already on the resume, this makes that claim tangible and demonstrable in a live interview.

---

### 10. Load / Performance — Expanded

**SoFi JD:** *"Architect and implement solutions that accelerate load and performance testing. Experience with load testing (e.g., Locust, Artillery)."*

**Gap:** `equiflow-load-test.jmx` covers order submission only (5,000 concurrent users). No load test exists for stop-loss evaluation or portfolio summary.

**Prerequisites:** EQ-101 (stop-loss logic), EQ-103 (portfolio endpoint).

**Planned: `stop-loss-evaluation-load.jmx`**
- Pre-load 1,000 stop-loss orders across 10 tickers
- Fire 50 concurrent price tick updates targeting those tickers
- Assert: no duplicate triggers; all triggered orders reach `FILLED`
- Assert: p99 trigger-to-fill latency < 1,000ms

**Planned: `portfolio-summary-load.jmx`**
- 500 concurrent `GET /portfolio/summary` requests
- Each makes a live Feign call to `market-data-service`
- Assert: p99 < 300ms; 0 HTTP 503 responses under normal load

---

### 11. Compliance Enriched Response

**SoFi JD:** *"Expertise in automated testing strategies... API contract and data validation."*

**Gap:** `ComplianceServiceTest` verifies `approved: false` and `violations[0].code = "WASH_SALE"`. The `ComplianceResult` DTO has no `complianceReason`, `blockedUntil`, or `triggerOrderId` fields — they don't exist in the codebase.

**Prerequisite (EQ-203):** Add `complianceReason`, `blockedUntil`, `triggerOrderId` to `ComplianceResult` DTO and implement the lookup logic in `WashSaleService`.

**Planned: `WashSaleEnrichedResponseTest`**
`compliance-service/src/test/java/com/equiflow/compliance/WashSaleEnrichedResponseTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `washSaleRejection_includesReason` | Sell AAPL at loss → buy within 30 days | `complianceReason = "Wash-sale rule: repurchase within 30 days of a loss sale"` |
| `washSaleRejection_blockedUntilIsCorrect` | Sale date = `2026-03-01` | `blockedUntil = "2026-03-31"` (sale date + 30 days) |
| `washSaleRejection_includesTriggerOrderId` | Prior sale order ID = `ord-8821` | `triggerOrderId = "ord-8821"` |
| `washSaleRejection_multipleSales_mostRecentWins` | Sales on `2026-02-01` and `2026-03-01` | `blockedUntil = "2026-03-31"` — most recent sale used |
| `washSaleRejection_missingHistory_triggerOrderIdIsNull` | No wash-sale history record in DB | `triggerOrderId: null`; no exception; rest of rejection response intact |
| `washSaleAndInsufficientFunds_bothViolationsReturned` | Sell AAPL at loss + buy 1,000 AAPL (cost > available cash) | `violations` array contains both `WASH_SALE` and `INSUFFICIENT_FUNDS`; `approved: false` |

---

## Infrastructure Changes

These changes are required to support the test work above. None involves modifying production application logic.

| Change | File | Purpose | SoFi Requirement |
|--------|------|---------|-----------------|
| Add WireMock container | `docker-compose.yml` | Stub external HTTP services (ACH bank, third-party APIs) at container level for local + CI testing | API mocking / test tenancy |
| Add Testcontainers to `ledger-service` pom | `ledger-service/pom.xml` | Real Postgres required for `SELECT FOR UPDATE` concurrency tests — H2 does not enforce the same locking semantics | Distributed systems testing |
| Add Testcontainers to `saga-orchestrator` pom | `saga-orchestrator/pom.xml` | Real Kafka required for saga compensation integration tests — embedded Kafka drops messages under concurrent load | Distributed systems testing |
| Add `docker-compose.test.yml` | project root | Lean CI stack — omits surge-simulator; faster startup; prevents port conflicts between parallel test runs | Shift-left / CI/CD |
| Isolated Kafka consumer groups for tests | `application-test.yml` per service | Prevents test runs from consuming each other's topic offsets; each test run gets a unique consumer group ID | Automated testing in distributed systems |
| Wire Allure to GitHub Actions | `.github/workflows/ci.yml` | Visual test report published on every PR; standard for FinTech platform teams | CI/CD, test maturity |

---

## SoFi JD Coverage Summary

| JD Requirement | Covered By |
|----------------|-----------|
| Distributed systems architecture | Items 1 (saga compensation), 2 (concurrency) |
| Automated testing strategies at scale | Items 3, 4, 5, 6, 11 |
| API mocking, test tenancy, traffic routing | Item 4 (WireMock) + infrastructure |
| Chaos / failure injection (Gremlin, AWS FIS equivalent) | Item 7 (`chaos-recovery.spec.ts`) |
| CI/CD, shift-left, continuous delivery | Item 8 (GitHub Actions) + infrastructure |
| AI-driven test automation | Item 9 (`tools/ai-test-generator/`) |
| Load / performance testing | Item 10 (expanded JMeter suites) |
| Java, Kotlin, Go, Python | Java/Kotlin in codebase; Go in Tala experience; Python in item 9 |
| AWS, Docker, Kubernetes | `docker-compose.yml`; AWS referenced in Altruist resume bullets |
