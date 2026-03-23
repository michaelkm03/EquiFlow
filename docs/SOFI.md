# EquiFlow — SoFi Test Platform Alignment Plan
**Role:** Senior Software Engineer — Test Platform
**Req ID:** 7507199003
**Status:** Planning — No code written yet
**Last Updated:** 2026-03-22

---

## Purpose

This document maps the SoFi Test Platform job requirements directly to planned
work in EquiFlow. Each item identifies the SoFi requirement it satisfies, what
currently exists in the codebase, the exact gap, and the specific work planned
to close it.

Where a test depends on feature code that does not yet exist, that dependency
is called out explicitly under **Prerequisites**.

---

## What Already Exists (Do Not Duplicate)

### Unit Tests — confirmed by reading source files

| File | Service | What It Actually Tests |
|------|---------|----------------------|
| `AuditServiceTest` | audit-service | Append-only persistence; retrieval by order ID; multiple events for same order |
| `AuthServiceTest` | auth-service | Valid login returns JWT; invalid password throws; unknown user throws; token validity |
| `ComplianceServiceTest` | compliance-service | Wash-sale rejection (code only, not field content); insufficient funds; valid order approval |
| `OrderServiceTest` | order-service | Market order fills during market hours; closed market throws `IllegalStateException`; LIMIT without price throws; `MarketHoursValidator` boolean smoke test |
| `SagaOrchestratorTest` | saga-orchestrator | **Happy path only** — all 5 steps succeed; compliance failure stops saga; `startSaga` creates record |
| `SettlementServiceTest` | settlement-service | T+1 calculation; weekend/holiday skipping; Friday → Monday |

> **Note:** `ledger-service` has no test class at all — the test directory does not exist.

### E2E Tests (Playwright) — confirmed by reading source files

| File | What It Actually Tests |
|------|----------------------|
| `auth.spec.ts` | Login happy/sad path; JWT validate endpoint |
| `compliance.spec.ts` | Funds approval; insufficient funds rejection; check ID returned; history endpoint; missing fields → 400 |
| `orders.spec.ts` | Market BUY; LIMIT BUY; LIMIT without price rejected; order book shape; pagination shape (`page=0&size=10`); poll to terminal state |

> **Note:** `orders.spec.ts` pagination test verifies response shape only — not filter params (`status`, `ticker`, `from`, `to`).
> No E2E test crosses into settlement, audit log, or ledger balance verification.
> No test uses the chaos (surge-simulator) API at all.

### Performance Tests

| File | What It Covers |
|------|---------------|
| `equiflow-load-test.jmx` | Order submission only — 5,000 concurrent users |

### Key Implementation Facts (verified in source)

| Fact | Impact on Planning |
|------|--------------------|
| `OrderType.STOP_LOSS` enum exists | Stop-loss type is declared; zero service logic handles it |
| `LedgerClient.release()` exists in saga Feign client | Compensation can call it immediately — no new client method needed |
| `OrderClient` only has `triggerMatch()` — no `cancelOrder()` | EQ-113 must add this method before compensation test is valid |
| `ComplianceResult` DTO has no enriched fields | `complianceReason`, `blockedUntil`, `triggerOrderId` don't exist — EQ-203 requires DTO + logic changes before tests |
| `failSaga()` sets status to FAILED and saves — nothing else | Compensation is entirely unimplemented; `LedgerClient.release()` is never called |

---

## Table of Contents

⚪ Not Started &nbsp;|&nbsp; 🔵 In Progress &nbsp;|&nbsp; ✅ Done

| # | Status | SoFi Requirement | Planned Work | Type | Prereqs | Priority |
|---|--------|-----------------|--------------|------|---------|----------|
| 1 | ⚪ | [Distributed Systems — Saga Compensation](#1-distributed-systems--saga-compensation) | `SagaCompensationIntegrationTest` | Integration Test | Add `cancelOrder()` to `OrderClient` | P0 |
| 2 | ⚪ | [Distributed Systems — Concurrency](#2-distributed-systems--concurrency) | `LedgerServiceConcurrencyTest` | Unit + Integration Test | None — `hold()`, `release()`, `debit()` exist | P0 |
| 3 | ⚪ | [Automated Testing — Stop-Loss Order Type](#3-automated-testing--stop-loss-order-type) | `StopLossOrderServiceTest` | Unit Test | Implement stop-loss trigger logic (EQ-101) | P0 |
| 4 | ⚪ | [API Mocking / Contract Testing](#4-api-mocking--contract-testing) | `PortfolioSummaryContractTest` | Contract Test | Implement portfolio endpoint (EQ-103) | P0 |
| 5 | ⚪ | [E2E — Full Trade Lifecycle](#5-e2e--full-trade-lifecycle) | `trading-lifecycle.spec.ts` | E2E Test | None — tests existing order + settlement + audit endpoints | P0 |
| 6 | ⚪ | [E2E — Stop-Loss](#6-e2e--stop-loss) | `stop-loss-lifecycle.spec.ts` | E2E Test | Implement stop-loss trigger logic (EQ-101) | P1 |
| 7 | ⚪ | [Chaos / Failure Injection Testing](#7-chaos--failure-injection-testing) | `chaos-recovery.spec.ts` | E2E + Chaos Test | None — surge-simulator API already exists | P1 |
| 8 | ⚪ | [CI/CD — Shift-Left Pipeline](#8-cicd--shift-left-pipeline) | `.github/workflows/ci.yml` | Infrastructure | None | P0 |
| 9 | ⚪ | [AI-Driven Test Automation](#9-ai-driven-test-automation) | `tools/ai-test-generator/` | AI Tool | None | P1 |
| 10 | ⚪ | [Load / Performance — Expanded](#10-load--performance--expanded) | Stop-loss + portfolio load tests | Performance Test | EQ-101, EQ-103 | P2 |
| 11 | ⚪ | [Compliance — Enriched Response Testing](#11-compliance--enriched-response-testing) | `WashSaleEnrichedResponseTest` | Unit + E2E Test | Add enriched fields to `ComplianceResult` (EQ-203) | P1 |

---

## Planned Work — Detail

---

### 1. Distributed Systems — Saga Compensation

**SoFi JD:**
> "Strong understanding of software design principles and distributed systems
> architecture... Architect and implement solutions that accelerate integration
> and chaos testing."

**What currently exists:**
- `SagaOrchestratorTest` tests happy path only — all 5 saga steps succeed
- `failSaga()` in `OrderSaga.java` sets status to `FAILED` and saves — nothing else
- `LedgerClient.release()` exists and is ready to call
- `OrderClient` has only `triggerMatch()` — **no `cancelOrder()` method exists**

**Prerequisite (feature code change):**
Add `cancelOrder()` to `OrderClient` Feign interface and a corresponding
`DELETE /orders/{id}` or `POST /orders/{id}/cancel` endpoint to `order-service`.
Without this, compensation at the matching step cannot be implemented or tested.

**Planned test: `SagaCompensationIntegrationTest`**

Location: `saga-orchestrator/src/test/java/com/equiflow/saga/SagaCompensationIntegrationTest.java`

Uses Testcontainers (real Postgres + real Kafka — no mocks):

| Test Case | Setup | Assert |
|-----------|-------|--------|
| `happyPath_allStepsComplete` | All 5 steps succeed | Saga = `COMPLETED`; hold converted to debit; balance reduced |
| `failAtSettlement_holdIsReleased` | Steps 1–4 pass; settlement-service stubbed to throw | `LedgerClient.release()` called; account balance fully restored |
| `failAtMatching_noHoldPlaced` | Compliance passes; order-service stubbed to throw | No hold placed; balance unchanged; saga = `FAILED` |
| `compensationIsIdempotent` | `failSaga()` called twice on same saga | Balance not double-released; no exception thrown |

**Why Testcontainers (not H2):**
H2 does not enforce `SELECT FOR UPDATE` the same way Postgres does. Compensation
correctness must be verified against real Postgres to be meaningful.

---

### 2. Distributed Systems — Concurrency

**SoFi JD:**
> "Proven programming skills in developing enterprise scale systems."

**What currently exists:**
- `LedgerService` has `hold()`, `release()`, `debit()` methods using `SELECT FOR UPDATE`
- **No `LedgerServiceTest` exists at all** — the test directory does not exist
- No concurrency test exists anywhere in the project

**Prerequisites:** None. All `LedgerService` methods are already implemented.

**Planned test: `LedgerServiceConcurrencyTest`**

Location: `ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceConcurrencyTest.java`

Two test classes:

**`LedgerServiceTest`** (unit, Mockito):

| Test Case | Assert |
|-----------|--------|
| `hold_reducesAvailableBalance` | Available balance decreases by hold amount |
| `release_restoresAvailableBalance` | Available balance restored after release |
| `debit_reducesBalanceAndReleasesHold` | Balance debited; hold released atomically |
| `hold_insufficientFunds_throwsException` | `InsufficientFundsException` thrown; balance unchanged |

**`LedgerServiceConcurrencyTest`** (integration, Testcontainers Postgres):

| Test Case | Setup | Assert |
|-----------|-------|--------|
| `concurrentHolds_onlyOneFills` | Two threads each request $800 hold on $1,000 account simultaneously via `CountDownLatch` | Exactly one succeeds; one throws `InsufficientFundsException`; final balance = $200 |
| `concurrentDebits_balanceIsConsistent` | Two threads debit simultaneously on same account | Final balance matches exactly one debit; no lost update |

`CountDownLatch` is used to synchronize thread start to the same instant —
the only reliable way to surface a `SELECT FOR UPDATE` race condition in a test.

---

### 3. Automated Testing — Stop-Loss Order Type

**SoFi JD:**
> "Expertise in automated testing strategies... Design, develop, and maintain
> software and systems that enable engineers to test backend applications."

**What currently exists:**
- `OrderType.STOP_LOSS` enum value exists
- `OrderServiceTest` covers `MARKET` and `LIMIT` only
- **No service logic handles `STOP_LOSS`** — no `PENDING_TRIGGER` status, no trigger evaluation, no price-tick listener

**Prerequisite (feature code change — EQ-101):**
Before tests can be written, the following must be implemented:
- Add `PENDING_TRIGGER` and `TRIGGERED` to `OrderStatus` enum
- Add `triggerPrice` column to the `orders` table (Flyway migration)
- Add trigger evaluation logic in `order-service` — on each price tick from `market-data-service`, evaluate open stop-loss orders for that ticker
- Add `order.stop-loss.triggered` Kafka topic

**Planned test: `StopLossOrderServiceTest`**

Location: `order-service/src/test/java/com/equiflow/order/StopLossOrderServiceTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `submitStopLoss_statusIsPendingTrigger` | Valid stop-loss submitted | Status = `PENDING_TRIGGER`; not sent to matching engine |
| `priceBelowTrigger_orderTriggered` | Price tick at `triggerPrice - 0.01` | Status = `TRIGGERED`; Kafka event published |
| `priceAtTrigger_orderTriggered` | Price tick exactly at `triggerPrice` | Status = `TRIGGERED` (boundary condition) |
| `priceAboveTrigger_orderNotTriggered` | Price tick at `triggerPrice + 0.01` | Status unchanged = `PENDING_TRIGGER` |
| `cancelledOrder_neverTriggers` | Order cancelled; price crosses trigger | Evaluation skipped; status stays `CANCELLED` |
| `marketClosed_triggerQueues` | Price hits trigger outside NYSE hours | Order queued; not converted to market order until market open |
| `twoStopLossOrders_bothTriggerIndependently` | Two orders, same ticker, different trigger prices | Each evaluates independently; no cross-order interference |

---

### 4. API Mocking / Contract Testing

**SoFi JD:**
> "Expertise in automated testing strategies, testing in production, **test
> tenancy, API mocking, traffic capture, routing and playback technologies.**"

**What currently exists:**
- **No WireMock, MockServer, or contract tests anywhere in the project**
- Unit tests use only in-process Mockito mocks
- This is the single JD requirement with zero coverage in the project

**Prerequisite (feature code change — EQ-103):**
`GET /portfolio/summary` on `ledger-service` does not exist yet. It must be
implemented before a contract test for it can be written.

**Planned test: `PortfolioSummaryContractTest`**

Location: `ledger-service/src/test/java/com/equiflow/ledger/PortfolioSummaryContractTest.java`

Uses WireMock to stub `market-data-service` at the HTTP level — distinct from
Mockito (which mocks Java interfaces, not HTTP calls):

| Test Case | WireMock Stub | Assert |
|-----------|--------------|--------|
| `portfolioSummary_calculatesCorrectPnl` | `GET /prices/AAPL` → `$155.00` | P&L math correct given known cost basis stored in DB |
| `portfolioSummary_marketDataDown_returns503` | `GET /prices/*` → HTTP 503 | Portfolio endpoint returns HTTP 503; no stale or partial data |
| `portfolioSummary_unknownTicker_excludedWithError` | `GET /prices/UNKNWN` → HTTP 404 | Position excluded from totals; appears in `pricingErrors` array |
| `portfolioSummary_noPositions_returns200` | No stubs needed | HTTP 200; empty `positions` array; all aggregate totals = 0 |

**Why WireMock, not Mockito:**
WireMock stubs the HTTP wire — it proves `ledger-service` makes the correct
HTTP call to the correct URL with the correct headers. Mockito cannot verify
this. This is the "API mocking" competency SoFi explicitly requires.

---

### 5. E2E — Full Trade Lifecycle

**SoFi JD:**
> "Deliver software that enables seamless testing of backend systems in
> cloud-native, containerized, and CI/CD environments, supporting **shift-left
> and continuous delivery.**"

**What currently exists:**
- `orders.spec.ts` submits orders and polls for a terminal status
- **No E2E test verifies settlement was created after a fill**
- **No E2E test verifies the audit log after a trade**
- **No E2E test touches the ledger balance before and after a fill**

**Prerequisites:** None. All endpoints (orders, settlement, audit, ledger) already exist.

**Planned test: `trading-lifecycle.spec.ts`**

Location: `tests/e2e/tests/trading-lifecycle.spec.ts`

| Test Case | Steps | Final Assert |
|-----------|-------|-------------|
| `fullTradeCycle_balanceReducedAfterFill` | Submit BUY → poll until `FILLED` → `GET /ledger/accounts/{userId}` | `availableCash` decreased by fill value |
| `fullTradeCycle_settlementCreated` | Submit BUY → poll `FILLED` → `GET /settlements?orderId={id}` | Settlement record exists with correct T+1 date |
| `fullTradeCycle_auditEventLogged` | Submit BUY → poll `FILLED` → `GET /audit/events?orderId={id}` | At least one audit event present for the order |
| `washSaleBlock_enrichedFields_present` | Sell AAPL at loss → submit BUY AAPL immediately → check rejection body | Response body contains `violations` array with `WASH_SALE` code |

---

### 6. E2E — Stop-Loss Lifecycle

**SoFi JD:**
> "Experience developing in a cloud environment... E2E testing."

**What currently exists:** No stop-loss tests at any layer.

**Prerequisite:** EQ-101 implemented (see item 3).

**Planned test: `stop-loss-lifecycle.spec.ts`**

Location: `tests/e2e/tests/stop-loss-lifecycle.spec.ts`

| Test Case | Steps | Final Assert |
|-----------|-------|-------------|
| `stopLoss_triggersOnPriceDrop` | Submit stop-loss → call `POST /admin/market/price` to set price below trigger → poll order | Status: `PENDING_TRIGGER` → `TRIGGERED` → `FILLED` |
| `stopLoss_cancelBeforeTrigger` | Submit stop-loss → `DELETE /orders/{id}` → update price below trigger | Order stays `CANCELLED`; no execution occurs |
| `stopLoss_historyIncludesBothPrices` | Submit and trigger stop-loss → `GET /orders/{id}` | Response includes both `triggerPrice` and `fillPrice` |

---

### 7. Chaos / Failure Injection Testing

**SoFi JD:**
> "Architect and implement solutions that accelerate... **chaos testing**...
> Experience with failure injection and chaos testing (Gremlin, AWS FIS)."

**What currently exists:**
- `surge-simulator` is a fully implemented chaos injection service
- It exposes `POST /admin/chaos/start`, `GET /admin/chaos/status`, `POST /admin/chaos/stop`
- **No test exercises the chaos API at all** — the entire surge-simulator is
  production code with zero test coverage

**Prerequisites:** None. The chaos API is fully implemented.

**Planned test: `chaos-recovery.spec.ts`**

Location: `tests/e2e/tests/chaos-recovery.spec.ts`

This is the EquiFlow equivalent of Gremlin / AWS FIS testing:

| Test Case | Chaos Config | Assert |
|-----------|-------------|--------|
| `dbFailure_sagasFailGracefully` | 50% DB failure rate; submit 10 orders | No order stuck permanently; each is either `FILLED` or `FAILED` with a reason |
| `networkLatency_ordersStillEventuallyFill` | 2,000ms latency; submit 1 order | Order reaches terminal state within 30s; no unhandled timeout error |
| `chaosStop_systemRecoversCleanly` | Start chaos; stop chaos; submit fresh order | Fresh order fills cleanly with normal latency |
| `highFailureRate_noNegativeBalances` | 80% DB failure; 20 concurrent orders | All account balances ≥ 0 after chaos; no stuck holds |

---

### 8. CI/CD — Shift-Left Pipeline

**SoFi JD:**
> "Deliver software that enables seamless testing in **CI/CD environments,
> supporting shift-left and continuous delivery.**
> Familiarity with CI/CD pipelines and tools (e.g., Argo, **GitLab CI/CD**)."

**What currently exists:** No CI pipeline. Tests must be run manually.

**Prerequisites:** None.

**Planned file: `.github/workflows/ci.yml`**

| Stage | Command | Fails PR If |
|-------|---------|------------|
| `build` | `mvn --batch-mode verify -DskipTests` | Compilation error |
| `unit-tests` | `mvn --batch-mode test` | Any unit test fails |
| `integration-tests` | `mvn --batch-mode verify` (Testcontainers) | Any integration test fails |
| `e2e-tests` | `docker-compose up -d` → `npx playwright test` | Any E2E test fails |
| `allure-report` | `allure generate` → publish to GitHub Pages | Never blocks; informational |

Triggers: `push` and `pull_request` to `master`

README displays live CI status badge.

---

### 9. AI-Driven Test Automation

**SoFi JD:**
> "Provide technical leadership with a focus on **integrating AI-driven
> automation and autonomous testing practices.**
> Research, prototype, and **productionize AI/ML tools to enhance developer
> productivity, test coverage, and test maturity.**"

**What currently exists:** No AI tooling in the project.

**Prerequisites:** None.

**Planned tool: `tools/ai-test-generator/`**

```
tools/
└── ai-test-generator/
    ├── generate_tests.py      # CLI entry point — reads OpenAPI spec, outputs .java test files
    ├── spec_parser.py         # Parses openapi.yaml; extracts endpoints, request/response schemas
    ├── claude_client.py       # Calls Claude API with a structured prompt per endpoint
    ├── test_writer.py         # Writes generated test skeletons into correct service test directory
    ├── requirements.txt       # anthropic, pyyaml
    └── README.md
```

**Example invocation:**
```bash
python generate_tests.py --service order-service --output order-service/src/test/
```

**What it generates per endpoint:**
- One happy path test with a valid request body
- Top 3 edge cases: invalid input, missing auth, boundary values
- Mockito stubs for service-layer dependencies
- TestNG `@Test` annotations with descriptive method names

**Why this matters for SoFi:**
This directly implements "AI for automated test generation" — the most
differentiating requirement on the JD. Michael already has it on his
resume with a 90% time reduction metric at Altruist; this tool makes
that claim tangible and demonstrable in a live whiteboard session.

---

### 10. Load / Performance — Expanded

**SoFi JD:**
> "Architect and implement solutions that accelerate integration, **load,
> performance**... testing."
> "Experience with load testing (e.g., Locust, Artillery)."

**What currently exists:**
- `equiflow-load-test.jmx` — order submission only, 5,000 concurrent users

**Prerequisites:** EQ-101 (stop-loss logic) and EQ-103 (portfolio endpoint) must be implemented.

**Planned: `stop-loss-evaluation-load.jmx`**
- Pre-load 1,000 stop-loss orders across 10 tickers
- Fire 50 concurrent price tick updates targeting those tickers
- Assert: no duplicate triggers; all triggered orders reach `FILLED`
- Assert: p99 trigger-to-fill latency < 1,000ms

**Planned: `portfolio-summary-load.jmx`**
- 500 concurrent `GET /portfolio/summary` requests
- Each request makes a live call to `market-data-service`
- Assert: p99 < 300ms; 0 HTTP 503 responses under normal load

---

### 11. Compliance — Enriched Response Testing

**SoFi JD:**
> "Expertise in automated testing strategies... API contract and data
> validation."

**What currently exists:**
- `ComplianceServiceTest.testWashSaleBlock()` verifies `approved = false` and violation code = `WASH_SALE`
- **`ComplianceResult` DTO has no enriched fields** — `complianceReason`, `blockedUntil`,
  and `triggerOrderId` do not exist in the codebase
- The rejection response content beyond `violations[].code` is never tested

**Prerequisite (feature code change — EQ-203):**
Add `complianceReason`, `blockedUntil`, and `triggerOrderId` fields to
`ComplianceResult` DTO and implement the lookup logic in `WashSaleService`
before writing assertions against those fields.

**Planned test: `WashSaleEnrichedResponseTest`**

Location: `compliance-service/src/test/java/com/equiflow/compliance/WashSaleEnrichedResponseTest.java`

| Test Case | Scenario | Assert |
|-----------|----------|--------|
| `washSaleRejection_includesReason` | Sell AAPL at loss → buy within 30 days | `complianceReason` field present and non-empty |
| `washSaleRejection_blockedUntilIsCorrect` | Sale date known | `blockedUntil` = sale date + 30 days |
| `washSaleRejection_includesTriggerOrderId` | Prior sale order ID known | `triggerOrderId` matches the selling order's ID |
| `washSaleRejection_multipleSales_mostRecentWins` | Two loss sales same ticker | `blockedUntil` calculated from most recent sale |
| `washSaleRejection_missingHistory_triggerOrderIdIsNull` | No wash-sale history record | Returns `triggerOrderId: null`; no exception thrown |

---

## Infrastructure Recommendations

These environment and tooling changes support the test work above and
align with SoFi's platform-team standards.

| Change | Purpose | SoFi Requirement It Supports |
|--------|---------|------------------------------|
| Add WireMock container to `docker-compose.yml` | Stub external HTTP dependencies (ACH bank, future third-party services) at the container level | API mocking / test tenancy |
| Add Testcontainers dependency to `ledger-service` and `saga-orchestrator` pom.xml | Integration tests need real Postgres for `SELECT FOR UPDATE` — H2 does not enforce the same locking | Distributed systems testing |
| Add `docker-compose.test.yml` profile | Lean stack for CI — omits surge-simulator, uses faster startup config | Shift-left / CI/CD |
| Add isolated Kafka consumer group config for tests | Prevents test runs from interfering with each other's topic offsets | Automated testing in distributed systems |
| Wire Allure to GitHub Actions CI | Visual test report on every PR; standard for FinTech test platforms | CI/CD, test maturity |

---

## Implementation Order

| Priority | Item | Why This Order |
|----------|------|---------------|
| 1 | EQ-112: `LedgerServiceTest` (unit) | No prereqs; foundational; closes the only service with zero tests |
| 2 | EQ-113: Add `cancelOrder()` to `OrderClient` + `SagaCompensationIntegrationTest` | Highest interview impact; fixes critical production gap |
| 3 | Item 5: `trading-lifecycle.spec.ts` | No prereqs; crosses service boundaries; shows E2E depth |
| 4 | Item 7: `chaos-recovery.spec.ts` | No prereqs; surge-simulator fully built; directly maps to Gremlin/AWS FIS |
| 5 | Item 8: `.github/workflows/ci.yml` | No prereqs; makes the repo look production-ready immediately |
| 6 | EQ-101: Stop-loss logic + `StopLossOrderServiceTest` | Large feature; prereq for items 6 and 10 |
| 7 | Item 4: WireMock `PortfolioSummaryContractTest` | Prereqs: EQ-103 portfolio endpoint |
| 8 | Item 9: AI test generator tool | No prereqs; biggest differentiator; save until core tests are solid |
| 9 | Items 10, 11 | Depend on EQ-101, EQ-103, EQ-203 being implemented |

---

## SoFi JD Requirement Coverage Summary

| JD Requirement | Addressed By |
|----------------|-------------|
| Distributed systems architecture | Items 1 (saga compensation), 2 (concurrency) |
| Automated testing strategies at scale | Items 3, 4, 5, 6, 11 |
| API mocking, test tenancy | Item 4 (WireMock) + infrastructure |
| Chaos / failure injection (Gremlin/AWS FIS) | Item 7 (chaos-recovery.spec.ts) |
| CI/CD, shift-left, continuous delivery | Item 8 (GitHub Actions) |
| AI-driven test automation | Item 9 (AI test generator) |
| Load / performance testing | Item 10 (expanded JMeter suites) |
| Java, Kotlin, Go, Python | Existing code (Java/Kotlin); Go in Tala experience; Python in item 9 |
| AWS, Docker, Kubernetes | Existing docker-compose; AWS in Altruist resume bullets |
