# EquiFlow — Product & Engineering Backlog
**Version:** 1.3
**Status:** Approved
**Product Owner:** Claude
**Engineering Lead:** Michael Montgomery
**Last Updated:** 2026-03-27

---

## Sign-Off

| Role | Name | Date |
|------|------|------|
| Product Owner | Claude | 2026-03-25 |
| Engineering Lead | Michael Montgomery | 2026-03-25 |

> Stories in **Sprint 1** are approved and scheduled. Stories in the **Backlog**
> are approved by product and engineering but not yet assigned to a sprint.
> All feature stories have been reviewed for technical feasibility by the
> engineering lead.

---

## Table of Contents

⚪ Not Started &nbsp; 🔵 In Progress &nbsp; ✅ Done

### Sprint 1 — Product Features
| Status | Ticket | Feature | Points | Priority |
|--------|--------|---------|--------|----------|
| ✅ | <nobr>[EQ-101](#eq-101--stop-loss-order-type)</nobr> | Stop-Loss Order Type — automatically sell a stock if its price drops to a level the user sets | 5 | P0 |
| ✅ | <nobr>[EQ-102](#eq-102--order-history--filtering-and-pagination)</nobr> | Order History Filtering & Pagination — search and page through past orders instead of getting one giant list | 3 | P0 |
| ⚪ | <nobr>[EQ-103](#eq-103--portfolio-pl-summary-endpoint)</nobr> | Portfolio P&L Summary — show users how much money they've made or lost across all their holdings | 5 | P1 |
| ⚪ | <nobr>[EQ-104](#eq-104--price-alerts--notify-when-target-price-is-hit)</nobr> | Price Alerts — let users set a target price on a stock and get notified when it hits that price | 5 | P1 |

### Sprint 1 — Infrastructure and Tech Debt
| Status | Ticket | Task | Points | Priority |
|--------|--------|------|--------|----------|
| ✅ | <nobr>[EQ-110](#eq-110--ci-pipeline-github-actions)</nobr> | CI Pipeline — GitHub Actions build and test on every push | 2 | P0 |
| ✅ | <nobr>[EQ-111](#eq-111--fix-java-version-mismatch)</nobr> | Fix Java Version Mismatch — align pom.xml and README to Java 21 | 1 | P0 |
| ✅ | <nobr>[EQ-112](#eq-112--ledgerservice-test-coverage)</nobr> | LedgerService Test Coverage — hold, debit, release, concurrency paths | 5 | P0 |
| ✅ | <nobr>[EQ-113a](#eq-113a--compensating-status-checkpoint)</nobr> | Compensating Status Checkpoint — write COMPENSATING to DB in failSaga() before any Feign call | 2 | P0 |
| ✅ | <nobr>[EQ-113b](#eq-113b--target-service-idempotency)</nobr> | Target Service Idempotency — system-cancel endpoint on order-service; release() idempotency on ledger-service | 2 | P0 — can start parallel with EQ-113a |
| ✅ | <nobr>[EQ-113c](#eq-113c--saga-compensation-wiring--recovery-job)</nobr> | Saga Compensation Wiring + Recovery — cancel/release steps in failSaga(); SagaStep recording; SagaRecoveryJob | 3 | P0 — depends on EQ-113a, EQ-113b |
| ⚪ | <nobr>[EQ-115](#eq-115--saga-settlement-failure--manual-reconciliation)</nobr> | Settlement Failure Handling — step 4 guard, COMPENSATION_REQUIRED status, ops Kafka alert, credit endpoint | 3 | P0 — depends on EQ-113c |
| ⚪ | <nobr>[EQ-116](#eq-116--saga-data-integrity-test-suite)</nobr> | Data Integrity Test Suite — Testcontainers end-to-end assertion of all three service DBs per compensation scenario | 3 | P0 — depends on EQ-115, EQ-113c |
| ⚪ | <nobr>[EQ-114](#eq-114--remove-redundant-synchronous-order-matching-in-submitorder)</nobr> | Remove Redundant Synchronous Matching — order matching runs twice; saga must be the sole execution path | 3 | P1 |

### Sprint 2 — Unit Test Coverage (95% JaCoCo)
| Status | Ticket | Task | Points | Priority |
|--------|--------|------|--------|----------|
| ⚪ | <nobr>[EQ-117](#eq-117--order-service-unit-test-coverage)</nobr> | Order-Service — MatchingEngine, OrderBook, StopLossService, OrderExpiryService | 3 | P0 |
| ⚪ | <nobr>[EQ-118](#eq-118--compliance-service-unit-test-coverage)</nobr> | Compliance-Service — WashSaleService full logic, edge cases | 2 | P0 |
| ⚪ | <nobr>[EQ-119](#eq-119--settlement-service-unit-test-coverage)</nobr> | Settlement-Service — SettlementService, SettlementScheduler | 2 | P0 |
| ⚪ | <nobr>[EQ-120](#eq-120--auth-service-unit-test-coverage)</nobr> | Auth-Service — JwtAuthFilter, RoleConverter, full auth paths | 2 | P0 |
| ⚪ | <nobr>[EQ-121](#eq-121--audit-service-unit-test-coverage)</nobr> | Audit-Service — AuditEventListener, full service paths | 1 | P0 |
| ⚪ | <nobr>[EQ-122](#eq-122--saga-orchestrator-unit-test-coverage)</nobr> | Saga-Orchestrator — SagaController, SagaEventListener, exception handling | 2 | P0 |
| ⚪ | <nobr>[EQ-123](#eq-123--market-data-service-unit-test-coverage)</nobr> | Market-Data-Service — full test suite from zero coverage | 5 | P0 |
| ⚪ | <nobr>[EQ-124](#eq-124--ledger-service-coverage-completion)</nobr> | Ledger-Service — LedgerController; enforce 95% JaCoCo rule | 1 | P0 |

### Backlog — Features
| Status | Ticket | Feature | Points |
|--------|--------|---------|--------|
| ⚪ | <nobr>[EQ-202](#eq-202--account-funding--deposit-and-withdrawal)</nobr> | Account Funding — let users add or withdraw money from their account | 8 |

---

## Story Point Scale

| Points | Effort |
|--------|--------|
| 1 | Trivial — under 1 hour |
| 2 | Small — half a day |
| 3 | Medium — 1 day |
| 5 | Large — 2–3 days |
| 8 | X-Large — 1 week |

---

## Sprint 1 — Core Platform Hardening
**Sprint Goal:** Stabilize the trading engine's financial core, establish CI,
and ship the stop-loss order type requested by early users.

---

### EQ-101 · Stop-Loss Order Type
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Order Management | Feature | 5 | P0 |

**Product Request:**
> "Users are asking for downside protection. We need stop-loss orders so they
> can set a trigger price and automatically exit a position if it drops to that
> level. This is table stakes for any retail trading platform."
> — Product, 2026-03-10

**Functionality:**
A stop-loss order is a passive, price-triggered order. When submitted, it is
stored with a `PENDING_TRIGGER` status and held outside the matching engine.
On every price tick from `MarketDataService`, all open stop-loss orders for
the updated ticker are evaluated. When the market price falls to or below the
`triggerPrice`, the stop-loss converts to a market order and is submitted
through the normal order execution flow via the saga orchestrator.

**Services Affected:**

| Service | Change |
|---------|--------|
| `order-service` | New `STOP_LOSS` order type; new `PENDING_TRIGGER` status; trigger evaluation logic |
| `market-data-service` | On each price update, query `order-service` for open stop-loss orders on that ticker |
| `saga-orchestrator` | No structural change; triggered stop-loss re-enters the existing saga flow as a market order |
| `audit-service` | Logs trigger event and subsequent execution |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `order-service` | Add `trigger_price NUMERIC(18,4)` column to `orders` table; add `PENDING_TRIGGER` to the order status enum; add index on `(ticker, status, trigger_price)` for efficient trigger evaluation |

**Kafka Topics:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `order.stop-loss.triggered` | `order-service` | `saga-orchestrator` | Signals that a stop-loss trigger condition was met and execution should begin |

**Happy Path:**
1. User submits `POST /orders` with `type: STOP_LOSS`, `ticker: AAPL`, `quantity: 10`, `triggerPrice: 150.00`
2. Order is persisted with status `PENDING_TRIGGER`; response confirms creation
3. `MarketDataService` publishes a price update: AAPL = 149.80
4. `order-service` evaluates open stop-loss orders for AAPL; trigger condition met
5. Order status updates to `TRIGGERED`; a `order.stop-loss.triggered` event is published
6. Saga executes the order as a market order; order fills and status updates to `FILLED`
7. Both `triggerPrice` and `fillPrice` are visible in order history

**Edge Cases:**
- Price drops below trigger but market is closed → order queues and executes at next market open; no duplicate trigger
- User cancels a `PENDING_TRIGGER` order before it fires → status transitions to `CANCELLED`; trigger evaluation skips cancelled orders
- Multiple stop-loss orders on same ticker trigger simultaneously → each is evaluated and executed independently; no cross-order interference
- Price briefly dips below trigger then recovers within the same tick cycle → trigger fires on first evaluation; no rollback of trigger state
- User submits a stop-loss with `triggerPrice` above current market price → accepted and stored; will never trigger unless price rises then falls

**Acceptance Criteria:**
- [ ] `POST /orders` accepts `"type": "STOP_LOSS"` with a `triggerPrice` field
- [ ] Order is stored with status `PENDING_TRIGGER` and does not enter the matching engine immediately
- [ ] `MarketDataService` price updates are evaluated against all open stop-loss orders for that ticker
- [ ] When market price ≤ `triggerPrice`, order converts to a market order and is submitted for execution
- [ ] If trigger fires outside NYSE hours, order is queued for next market open
- [ ] Triggered and filled stop-loss orders appear in order history with both `triggerPrice` and `fillPrice`

---

### EQ-102 · Order History — Filtering and Pagination
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Order Management | Feature | 3 | P0 |

**Product Request:**
> "Right now users get a flat dump of every order they've ever placed. We need
> date range filtering and pagination — the list is unusable once a user has
> more than a few dozen orders."
> — Product, 2026-03-08

**Functionality:**
`GET /orders` currently fetches all orders for the authenticated user in a single
unfiltered query. This story adds server-side filtering by date range, order
status, and ticker, plus offset-based pagination. No new data is created or
modified — this is a query-layer change only.

**Services Affected:**

| Service | Change |
|---------|--------|
| `order-service` | Update `OrderController` and `OrderRepository` to accept filter params and return paginated results |
| `api-gateway` | No change — passes query params through transparently |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `order-service` | Add composite index on `(user_id, created_at, status, ticker)` to support filtered queries without full table scans |

**Kafka Topics:** None — read-only operation.

**Happy Path:**
1. User calls `GET /orders?from=2026-01-01&to=2026-03-01&status=FILLED&page=0&size=25`
2. `order-service` queries orders scoped to the authenticated user with all filters applied
3. Response returns up to 25 matching orders plus a `pagination` object with total count and page metadata

**Edge Cases:**
- `from` date is after `to` date → HTTP 400 with a descriptive error
- `from` or `to` is not a valid ISO 8601 date → HTTP 400
- `size` exceeds 100 → clamped to 100 or HTTP 400; document which behavior is chosen
- No orders match the filters → HTTP 200 with an empty `orders` array and `totalElements: 0`
- `page` index exceeds total pages → HTTP 200 with an empty `orders` array; not a 404

**Acceptance Criteria:**
- [ ] `GET /orders` supports query params: `from`, `to` (ISO 8601 dates), `status`, `ticker`, `page`, `size`
- [ ] Default page size is 25; max is 100
- [ ] Response includes a `pagination` object with `totalElements`, `totalPages`, `currentPage`
- [ ] Invalid date format returns HTTP 400 with a descriptive error
- [ ] Filtering by `status=FILLED` returns only filled orders
- [ ] A request beyond the last page returns HTTP 200 with an empty result set

---

### EQ-103 · Portfolio P&L Summary Endpoint
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Portfolio Analytics | Feature | 5 | P1 |

**Product Request:**
> "Users have no way to see whether they're up or down overall. We need a
> portfolio summary — current value, total cost basis, and unrealized P&L per
> position. This is the most-requested feature from beta users."
> — Product, 2026-03-05

**Functionality:**
A new `GET /portfolio/summary` endpoint on `ledger-service` reads the
authenticated user's open positions (stored with weighted average cost basis)
and fetches current market prices from `market-data-service` via Feign. It
calculates unrealized P&L per position and returns an aggregate summary. This
is a read-only, synchronous operation — no new data is persisted.

**Services Affected:**

| Service | Change |
|---------|--------|
| `ledger-service` | New `GET /portfolio/summary` endpoint; new `PortfolioController` and P&L calculation logic; Feign client call to `market-data-service` |
| `market-data-service` | Expose `GET /prices/{ticker}` endpoint if not already available for Feign consumption |
| `api-gateway` | Add route for `/portfolio/**` → `ledger-service` |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `ledger-service` | No schema changes; reads from existing `positions` table (`user_id`, `ticker`, `quantity`, `avg_cost_basis`) |

**Kafka Topics:** None — synchronous read-only operation.

**Happy Path:**
1. User calls `GET /portfolio/summary`
2. `ledger-service` fetches all positions where `quantity > 0` for the authenticated user
3. For each position, fetches current price from `market-data-service`
4. Calculates: `marketValue = quantity × currentPrice`, `unrealizedPnl = marketValue − (quantity × avgCostBasis)`, `unrealizedPnlPct = unrealizedPnl / (quantity × avgCostBasis) × 100`
5. Aggregates totals across all positions and returns the full summary

**Edge Cases:**
- User has no positions → returns HTTP 200 with an empty `positions` array and all aggregate totals as `0`
- `market-data-service` is unavailable → return HTTP 503 with a clear error; do not return stale or partial data silently
- A position exists for a ticker that `market-data-service` has no price for → exclude that position from the response and include it in a `pricingErrors` array
- Position with `quantity = 0` (fully exited) → excluded from the response
- `avgCostBasis` is zero (data integrity issue) → skip P&L calculation for that position; return quantity and market value only, flag in response

**Acceptance Criteria:**
- [ ] `GET /portfolio/summary` returns all positions where `quantity > 0` for the authenticated user
- [ ] Each position includes: `ticker`, `quantity`, `avgCostBasis`, `currentPrice`, `marketValue`, `unrealizedPnl`, `unrealizedPnlPct`
- [ ] Response includes aggregate totals: `totalCostBasis`, `totalMarketValue`, `totalUnrealizedPnl`
- [ ] Current prices are fetched from `market-data-service` at request time
- [ ] If `market-data-service` is unreachable, the endpoint returns HTTP 503
- [ ] Positions with zero quantity are excluded

---

### EQ-104 · Price Alerts — Notify When Target Price Is Hit
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Notifications | Feature | 5 | P1 |

**Product Request:**
> "Users want to know when a stock hits their target price without having to
> watch the screen all day. Add price alerts — let them set a target and get
> notified when the price crosses it."
> — Product, 2026-03-11

**Functionality:**
Users configure a price alert by specifying a ticker, target price, and
direction (`ABOVE` or `BELOW`). Alerts are stored in `market-data-service`.
On every price tick, active alerts for the updated ticker are evaluated. When
the threshold is crossed, the alert is marked `TRIGGERED` and a Kafka event is
published. An initial notification handler logs the event to console; a real
delivery channel (email, push) is out of scope for this story.

**Services Affected:**

| Service | Change |
|---------|--------|
| `market-data-service` | New `AlertController` (`POST`, `GET`, `DELETE /alerts`); alert evaluation on price tick; Kafka publish on trigger |
| `audit-service` | Consumes `price.alert.triggered` events and appends to audit log |
| `api-gateway` | Add route for `/alerts/**` → `market-data-service` |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `market-data-service` | New `price_alerts` table: `id`, `user_id`, `ticker`, `target_price`, `direction` (`ABOVE`/`BELOW`), `status` (`ACTIVE`/`TRIGGERED`/`CANCELLED`), `created_at`, `triggered_at` |

**Kafka Topics:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `price.alert.triggered` | `market-data-service` | `audit-service` | Signals that a user's price alert threshold was crossed |

**Happy Path:**
1. User calls `POST /alerts` with `{ ticker: "TSLA", targetPrice: 300.00, direction: "ABOVE" }`
2. Alert is stored with status `ACTIVE`; response returns alert ID
3. `MarketDataService` publishes a price update: TSLA = 301.50
4. Alert evaluation finds a matching `ACTIVE` alert; threshold crossed
5. Alert status updates to `TRIGGERED`; `price.alert.triggered` event published to Kafka
6. `audit-service` consumes the event and logs it
7. Alert no longer appears in `GET /alerts` active results

**Edge Cases:**
- User creates duplicate alerts for the same ticker/price/direction → accept both; they are independent alerts
- Alert triggers while the market is closed → trigger fires regardless of market hours; price alerts are not bound to trading hours
- User deletes an alert at the same moment it is being evaluated → ensure atomic status check; a `CANCELLED` alert must not trigger
- Price oscillates across the threshold multiple times in rapid succession → alert fires exactly once; re-evaluation of `TRIGGERED` alerts is skipped
- User has 100+ active alerts across many tickers → alert evaluation per tick must query only alerts matching the updated ticker, not all alerts

**Acceptance Criteria:**
- [ ] `POST /alerts` accepts `{ ticker, targetPrice, direction: "ABOVE" | "BELOW" }` and returns the created alert with its ID
- [ ] `GET /alerts` returns all `ACTIVE` alerts for the authenticated user
- [ ] `DELETE /alerts/{id}` sets alert status to `CANCELLED`; returns HTTP 404 if the alert does not belong to the user
- [ ] When market price crosses the threshold, a `price.alert.triggered` Kafka event is published
- [ ] Triggered alert is marked `TRIGGERED` and excluded from future evaluations
- [ ] `audit-service` records the trigger event

---

## Platform Sprint 1 — Engineering
**Sprint Goal:** Establish CI and fix correctness gaps before feature work ships.
> These are internal engineering tasks. They are not user-facing but are
> required for the team to ship features reliably.

---

### EQ-110 · CI Pipeline (GitHub Actions)
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

**Services Affected:** All modules (build and test validation only — no runtime changes)

**Acceptance Criteria:**
- [ ] Workflow triggers on `push` and `pull_request` to `master`
- [ ] Pipeline runs `mvn --batch-mode test` across all modules on Java 21
- [ ] A failing test causes the pipeline to report failure
- [ ] README displays CI status badge

**Files:** `.github/workflows/ci.yml` *(new)*

---

### EQ-111 · Fix Java Version Mismatch
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 1 | P0 — blocks EQ-110 |

**Services Affected:** All modules (build configuration only)

**Acceptance Criteria:**
- [ ] `pom.xml` declares Java 21; README updated to match
- [ ] Build and all tests pass under Java 21 in CI

---

### EQ-112 · LedgerService Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 5 | P0 |

**Services Affected:** `ledger-service`

**New Files:**
```
ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceConcurrencyTest.java
```

**Modified Files:**
```
pom.xml                        — Lombok 1.18.38 (JDK 25 support); annotationProcessorPaths; --release flag
ledger-service/pom.xml         — Testcontainers dependency; Surefire DOCKER_HOST + api.version config
~/.testcontainers.properties   — docker.host=npipe:////./pipe/docker_engine_linux (machine-level)
```

**Infrastructure Fixes (required to run Testcontainers on this environment):**

| Issue | Fix |
|-------|-----|
| Lombok annotation processing silent failure under JDK 25 | Bumped `lombok.version` to `1.18.38`; declared Lombok in `<annotationProcessorPaths>` |
| `maven-compiler-plugin` `-source`/`-target` deprecation warning | Switched to `<release>` flag |
| Docker Desktop 29.x rejects docker-java's hardcoded API version 1.32 | Added `-Dapi.version=1.44` to Surefire `argLine`; `api.version` is the system property read by `DefaultDockerClientConfig` |
| Surefire forked JVM can't reach Docker named pipe | Added `DOCKER_HOST=npipe:////./pipe/docker_engine_linux` to Surefire `<environmentVariables>`; `docker_engine_linux` is the WSL2 Linux engine pipe |

`LedgerServiceConcurrencyTest` is the only second test file across any service — justified because concurrency requires a real Postgres container and must be kept separate from Mockito unit tests that run without infrastructure.

**Database Changes:** None — concurrency tests spin up a fresh Testcontainers Postgres with inline DDL; no migration files are modified.

**Test Cases — `LedgerServiceConcurrencyTest` (integration, Testcontainers Postgres)**

| Method | Scenario | Assert |
|--------|----------|--------|
| `postgres_containerStartsAndAcceptsQuery` | Smoke test — container starts and responds to `SELECT 1` | Container running; JDBC query returns `1` |
| `ledgerHold_concurrentRequests_onlyOneSucceeds` | Two threads race a $75 hold on a $100 account via `SELECT FOR UPDATE` | `successCount` = 1; `failCount` = 1; `cash_on_hold` = $75 |

**Acceptance Criteria:**
- [x] `postgres_containerStartsAndAcceptsQuery` passes — Docker/Testcontainers pipeline confirmed
- [ ] `ledgerHold_concurrentRequests_onlyOneSucceeds` passes against a real Postgres container
- [ ] `LedgerServiceTest` (unit, Mockito) — 12 test cases covering `hold`, `release`, `debit`, `updatePosition`, `getAccount`
- [ ] `LedgerService` line coverage ≥ 80% per JaCoCo report

**Measuring Coverage**

JaCoCo is configured in the parent `pom.xml` and runs automatically during `mvn verify`. To generate and view the report for `ledger-service` only:

```bash
mvn verify -pl ledger-service
open ledger-service/target/site/jacoco/index.html      # Mac
start ledger-service/target/site/jacoco/index.html     # Windows
```

The HTML report shows each class with **line coverage %** (lines executed ÷ total executable lines) and **branch coverage %** (both sides of every `if`/`switch` hit ÷ total branches). Lines are highlighted green (covered), yellow (partially covered branch), or red (never executed). The 80% target applies to `LedgerService.java` line coverage specifically — not the whole module.

---

### EQ-113a · COMPENSATING Status Checkpoint
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope:** One targeted change to `failSaga()` in `saga-orchestrator`. No Feign calls, no recovery logic, no other files. Compensation wiring and the recovery job are EQ-113c.

**Change:**
In `OrderSaga.failSaga()`, before any Feign client is called:
```java
saga.setStatus("COMPENSATING");
sagaRepository.save(saga);
```
This is the crash-safe boundary. If the pod dies after this line, `SagaRecoveryJob` (EQ-113c) can find and resume the saga by querying for sagas in COMPENSATING state.

**Services Affected:** `saga-orchestrator` only.

**Acceptance Criteria:**
- [x] `failSaga()` saves saga with status=COMPENSATING before any downstream Feign call is made
- [x] A saga in COMPENSATING status is readable in the DB immediately after `failSaga()` is invoked, regardless of what downstream calls do

**Test Cases — `SagaCompensationTest` (unit, Mockito):**

| Scenario | Mock setup | Assert |
|----------|------------|--------|
| `failSaga()` called — any step | Downstream calls mocked (any return) | `sagaRepository.save()` called with status=COMPENSATING before any mock is invoked |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaCompensationTest#failSaga_setsCompensatingBeforeFeign
```

---

### EQ-113b · Target Service Idempotency
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 — can start in parallel with EQ-113a |

> **Scope:** Changes to `order-service` and `ledger-service` only. No changes to `saga-orchestrator`. These are the prerequisite service changes that EQ-113c will call via Feign.

**Changes:**

*order-service:*
- New endpoint: `POST /orders/{orderId}/system-cancel` (body: `{ userId }`) — validates order belongs to user; cancels the order; publishes `order.cancelled` Kafka event
- `OrderService.cancelOrder()` terminal state rules — covers all 10 `OrderStatus` values:

| Status | System-Cancel Behaviour |
|--------|------------------------|
| `PENDING` | Cancel → `CANCELLED`; publish `order.cancelled` |
| `COMPLIANCE_CHECK` | Cancel → `CANCELLED`; publish `order.cancelled` (mid-saga step 1, not yet matched) |
| `OPEN` | Cancel → `CANCELLED`; remove entry from order book; publish `order.cancelled` |
| `PENDING_TRIGGER` | Cancel → `CANCELLED`; publish `order.cancelled` (stop-loss not yet triggered) |
| `TRIGGERED` | Cancel → `CANCELLED`; publish `order.cancelled` (triggered but not yet re-matched) |
| `CANCELLED` | No-op — return silently; no DB write; no event |
| `REJECTED` | No-op — return silently; no DB write; no event |
| `FAILED` | No-op — return silently; no DB write; no event (already terminal) |
| `FILLED` | HTTP 409 `{ "error": "ORDER_IN_TERMINAL_STATE", "status": "FILLED", "orderId": "..." }`; log WARN — money already moved, ops must reconcile |
| `PARTIALLY_FILLED` | HTTP 409 `{ "error": "ORDER_IN_TERMINAL_STATE", "status": "PARTIALLY_FILLED", "orderId": "..." }`; log WARN — partial fill means money partially moved, treat same as FILLED |

*ledger-service:*
- Add to `LedgerTransactionRepository`:
  ```java
  boolean existsByOrderIdAndType(UUID orderId, String type);
  ```
- In `LedgerService.release()`, add an idempotency guard **after** `SELECT FOR UPDATE` and **before** any balance mutation:
  ```java
  if (request.getOrderId() != null &&
      transactionRepository.existsByOrderIdAndType(request.getOrderId(), "RELEASE")) {
      log.info("Duplicate release detected for orderId={}, returning current state", request.getOrderId());
      return toResponse(account);
  }
  ```

**Why the existing `.max(BigDecimal.ZERO)` floor is not sufficient:** it prevents `cashOnHold` from going negative, but a second call with the same amount still reduces the hold by that amount again before hitting the floor — meaning the first call releases $1,500 correctly, and a second call silently reduces the hold by another $1,500 (floored to 0 if already 0). The idempotency check blocks the second call before any mutation occurs.

**Transaction boundary:** the guard and any subsequent write must execute inside the same `@Transactional` block, after `findByUserIdForUpdate` acquires the row lock. This prevents a race where two concurrent calls both pass the idempotency check before either writes the RELEASE transaction.

**Null orderId:** releases without an `orderId` (manual ledger adjustments) skip the idempotency check — only saga-driven releases carry an orderId.

**Return value:** HTTP 200 in both the new-release and duplicate-release paths. The caller (EQ-113c compensation) does not need to distinguish between them.

**Services Affected:** `order-service`, `ledger-service`.

**Acceptance Criteria:**
- [x] `POST /orders/{orderId}/system-cancel` cancels a `PENDING`, `COMPLIANCE_CHECK`, `OPEN`, `PENDING_TRIGGER`, or `TRIGGERED` order; returns HTTP 200; publishes `order.cancelled`
- [x] System-cancel on `CANCELLED`, `REJECTED`, or `FAILED` returns HTTP 200 with no DB change
- [x] System-cancel on `FILLED` or `PARTIALLY_FILLED` returns HTTP 409 with `{ "error": "ORDER_IN_TERMINAL_STATE", "status": "...", "orderId": "..." }`; WARN logged
- [x] `LedgerService.release()` called twice with the same orderId: second call returns HTTP 200; `cash_on_hold` unchanged; no second RELEASE transaction written

**Test Cases — `OrderServiceTest` (unit, Mockito):**

*Cancellable states — saga compensation calls system-cancel; these orders have not yet been matched:*

| Method | Order Status | Assert |
|--------|-------------|--------|
| `systemCancel_pendingOrder_cancels` | `PENDING` | Status → `CANCELLED`; `orderRepository.save()` called; `order.cancelled` event published |
| `systemCancel_complianceCheckOrder_cancels` | `COMPLIANCE_CHECK` | Status → `CANCELLED`; event published (mid-step-1, nothing matched yet) |
| `systemCancel_openOrder_cancels` | `OPEN` | Status → `CANCELLED`; event published |
| `systemCancel_pendingTriggerOrder_cancels` | `PENDING_TRIGGER` | Status → `CANCELLED`; event published (stop-loss never fired) |
| `systemCancel_triggeredOrder_cancels` | `TRIGGERED` | Status → `CANCELLED`; event published (trigger fired but order not yet re-matched) |

*Already-terminal states — compensation called twice or after saga already resolved; must be safe no-ops:*

| Method | Order Status | Assert |
|--------|-------------|--------|
| `systemCancel_alreadyCancelled_isNoOp` | `CANCELLED` | Returns HTTP 200; `orderRepository.save()` not called; no event published |
| `systemCancel_rejectedOrder_isNoOp` | `REJECTED` | Returns HTTP 200; no DB write; no event |
| `systemCancel_failedOrder_isNoOp` | `FAILED` | Returns HTTP 200; no DB write; no event |

*Money-moved states — order was matched before compensation ran; ops must reconcile manually:*

| Method | Order Status | Assert |
|--------|-------------|--------|
| `systemCancel_filledOrder_returns409` | `FILLED` | HTTP 409; body contains `ORDER_IN_TERMINAL_STATE` and `orderId`; WARN logged; order status unchanged |
| `systemCancel_partiallyFilledOrder_returns409` | `PARTIALLY_FILLED` | HTTP 409; same body shape as FILLED; WARN logged; no status change |

*Negative / guard cases — bad input must not corrupt state:*

| Method | Scenario | Assert |
|--------|----------|--------|
| `systemCancel_orderNotFound_throws` | `orderId` does not exist in repository | `IllegalArgumentException` thrown; no event published |
| `systemCancel_wrongUser_throws` | `orderId` exists but belongs to a different `userId` | `IllegalArgumentException` thrown; order status unchanged |

**Test Cases — `LedgerServiceTest` (unit, Mockito):**

*Normal release path — verifies the baseline behaviour before testing idempotency:*

| Method | Scenario | Assert |
|--------|----------|--------|
| `release_firstCall_reducesHoldAndWritesTransaction` | `cashOnHold` = $1,500; release $1,500 for orderId X | `cashOnHold` → $0; one `RELEASE` transaction written; guard evaluated but returns false (no prior RELEASE exists) |

*Idempotency — the recovery job or a retry may call release() more than once for the same order:*

| Method | Scenario | Assert |
|--------|----------|--------|
| `release_duplicateOrderId_isNoOp` | `existsByOrderIdAndType(orderId, "RELEASE")` returns `true` | `cashOnHold` unchanged; `accountRepository.save()` not called; no second `RELEASE` transaction written; returns HTTP 200 |
| `release_nullOrderId_alwaysExecutes` | `orderId` is null on the request | Idempotency check skipped entirely; balance reduced; transaction written (manual adjustments have no orderId) |

*Edge / guard cases — protect balance integrity:*

| Method | Scenario | Assert |
|--------|----------|--------|
| `release_holdLessThanAmount_floorsAtZero` | `cashOnHold` = $500; release requested for $1,500 | `cashOnHold` floors at $0 (not negative); `availableCash` increases by $500 only |

```bash
mvn test -pl order-service -Dtest=OrderServiceTest
mvn test -pl ledger-service -Dtest=LedgerServiceTest
```

---

### EQ-113c · Saga Compensation Wiring + Recovery Job
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-113a, EQ-113b |

> **Scope:** `saga-orchestrator` only. Wires cancel + release Feign calls into `failSaga()`, records compensation SagaSteps, and adds the scheduled recovery job. EQ-113a (COMPENSATING checkpoint) and EQ-113b (`systemCancelOrder` + `release` idempotency) are prerequisites and already complete.

**Prerequisites already in place:**
- `OrderClient.systemCancelOrder(orderId, body)` — added in EQ-113b
- `LedgerClient.release(body)` — already existed
- `failSaga()` writes COMPENSATING before FAILED — added in EQ-113a

**Implementation Status:**

| Item | Status |
|------|--------|
| `failSaga(saga, reason, failedStep)` signature | ✅ Done |
| Switch-based compensation logic (case 1/2/3) | ✅ Done |
| `compensateCancel()` / `compensateRelease()` private helpers | ✅ Done |
| `COMPENSATION_CANCEL` / `COMPENSATION_RELEASE` SagaStep recording | ✅ Done |
| Structured compensation log lines | ✅ Done |
| Postman Flow G (3 subfolders, before/after DB assertions) | ✅ Done |
| Idempotency guard (skip already-COMPLETED steps on re-run) | ✅ Done |
| `SagaRepository.findByStatusAndUpdatedAtBefore()` | ✅ Done |
| `SagaRecoveryJob` (`@Scheduled`, 60s, 2-min cutoff) | ✅ Done |

**Changes:**

1. **`failSaga()` signature** — `int failedStep` parameter drives the compensation switch:
   ```java
   private Saga failSaga(Saga saga, String reason, int failedStep)
   ```
   All four call sites updated in `execute()`:
   - Lines 73, 79 — both step 1 paths: `failSaga(saga, reason, 1)`
   - Line 98 — step 2: `failSaga(saga, reason, 2)`
   - Line 132 — step 3: `failSaga(saga, reason, 3)`

2. **Compensation switch inside `failSaga()`** — after writing COMPENSATING (EQ-113a):
   ```java
   switch (failedStep) {
       case 1 -> { /* no-op — nothing committed */ }
       case 2 -> { compensateCancel(saga); }
       case 3 -> { compensateCancel(saga); compensateRelease(saga); }
   }
   ```

   | `failedStep` | Cancel Order | Release Hold |
   |-------------|-------------|-------------|
   | 1 — Compliance | No | No |
   | 2 — Matching | Yes | No |
   | 3 — Debit | Yes | Yes |

3. **`compensateCancel()` / `compensateRelease()` helpers** — each helper:
   - Creates a `SagaStep` with `stepNumber=0`, appropriate `stepName`
   - Calls the Feign client in a try/catch
   - Sets `status=COMPLETED` or `status=FAILED` + `errorMessage`
   - Appends the step to `saga.getSteps()` (persisted by `CascadeType.ALL` on the final save)
   - Independent try/catch per helper — cancel failure cannot block release

4. **Logging** — every compensation action:
   ```
   INFO  saga_compensation step=CANCEL  orderId={} userId={} outcome=COMPLETED
   ERROR saga_compensation step=RELEASE orderId={} userId={} outcome=FAILED reason={}
   ```

5. **Idempotency guard** *(remaining)* — before each Feign call, check `saga.getSteps()` for an existing `COMPLETED` step with that name — skip if found. Required for `SagaRecoveryJob` re-runs to be safe.

6. **`SagaRepository`** *(remaining)* — add Spring Data query:
   ```java
   List<Saga> findByStatusAndUpdatedAtBefore(String status, Instant cutoff);
   ```

7. **`SagaRecoveryJob`** *(remaining)* — `@Scheduled(fixedDelay = 60_000)`. Every 60 seconds:
   - Queries sagas in `COMPENSATING` with `updatedAt` older than 2 minutes
   - For each: reads `currentStep`, skips already-COMPLETED steps, calls missing compensation

**Services Affected:** `saga-orchestrator` only.

**Acceptance Criteria:**
- [x] Step 1 failure: `cancelOrder()` and `release()` are not called; saga reaches `FAILED`; no `COMPENSATION_*` SagaSteps written
- [x] Step 2 failure: `cancelOrder()` called exactly once with correct `orderId` + `userId`; `COMPENSATION_CANCEL` SagaStep written with `status=COMPLETED`; saga reaches `FAILED`
- [x] Step 3 failure: `cancelOrder()` and `release()` both called; `COMPENSATION_CANCEL` and `COMPENSATION_RELEASE` SagaSteps both written; saga reaches `FAILED`
- [x] Cancel Feign call throws: `COMPENSATION_CANCEL` SagaStep written with `status=FAILED` + error message; release still runs; `COMPENSATION_RELEASE` SagaStep written; saga reaches `FAILED`
- [x] Release Feign call throws: `COMPENSATION_RELEASE` SagaStep written with `status=FAILED` + error message; saga reaches `FAILED`; cancel result is unaffected
- [x] All compensation log lines include `orderId`, `userId`, step name, outcome, and reason on failure
- [x] `COMPENSATION_CANCEL` SagaStep already `COMPLETED`: `cancelOrder()` not called; release runs normally
- [x] `COMPENSATION_RELEASE` SagaStep already `COMPLETED`: `release()` not called; saga reaches `FAILED`
- [x] `SagaRecoveryJob` runs every 60 seconds; sagas in `COMPENSATING` newer than 2 minutes are skipped
- [x] Recovery job skips compensation steps whose SagaStep is already `COMPLETED`; calls only the missing ones

**Test Cases — `SagaCompensationTest` (unit, Mockito):**

*Compensation per failed step — verifies which Feign calls are made and what SagaSteps are recorded:*

| Method | Mock setup | Assert |
|--------|------------|--------|
| `failSaga_step1_noCompensationCalls` | `complianceClient.check()` throws | `cancelOrder()` never called; `release()` never called; no `COMPENSATION_*` SagaSteps; saga=`FAILED` |
| `failSaga_step2_cancelOrderCalled` | `orderClient.triggerMatch()` throws; `cancelOrder()` succeeds | `cancelOrder()` called once with correct orderId+userId; `COMPENSATION_CANCEL` step=`COMPLETED`; saga=`FAILED` |
| `failSaga_step3_cancelAndReleaseBothCalled` | `ledgerClient.debit()` throws; both Feign calls succeed | `cancelOrder()` and `release()` both called; both SagaSteps written as `COMPLETED`; saga=`FAILED` |

*Partial Feign failure — one compensation call fails, the other must still execute:*

| Method | Mock setup | Assert |
|--------|------------|--------|
| `failSaga_cancelFails_releaseStillRuns` | `cancelOrder()` throws; `release()` succeeds | `COMPENSATION_CANCEL` step=`FAILED` with error message; `COMPENSATION_RELEASE` step=`COMPLETED`; saga=`FAILED` |
| `failSaga_releaseFails_cancelUnaffected` | `cancelOrder()` succeeds; `release()` throws | `COMPENSATION_CANCEL` step=`COMPLETED`; `COMPENSATION_RELEASE` step=`FAILED` with error message; saga=`FAILED` |

*Idempotency — recovery job re-runs must skip already-completed steps:*

| Method | Mock setup | Assert |
|--------|------------|--------|
| `failSaga_cancelAlreadyCompleted_skipsCancel` | Saga has existing `COMPENSATION_CANCEL` step=`COMPLETED` | `cancelOrder()` not called; `release()` still runs; `COMPENSATION_RELEASE` SagaStep written |
| `failSaga_releaseAlreadyCompleted_skipsRelease` | Saga has existing `COMPENSATION_RELEASE` step=`COMPLETED` | `release()` not called; saga reaches `FAILED` |

**Test Cases — `SagaRecoveryJobTest` (unit, Mockito):**

*Scheduling and eligibility — only old enough COMPENSATING sagas are processed:*

| Method | Mock setup | Assert |
|--------|------------|--------|
| `recoveryJob_noCompensatingSagas_doesNothing` | Repository returns empty list | No compensation calls made |
| `recoveryJob_sagaYoungerThan2Min_isSkipped` | Saga `updatedAt` = 1 minute ago | Compensation not called; saga state unchanged |
| `recoveryJob_sagaOlderThan2Min_runsCompensation` | Saga `updatedAt` = 5 minutes ago, `currentStep=3`, no SagaSteps present | `cancelOrder()` and `release()` both called; both SagaSteps written |

*Step-aware recovery — only missing steps are re-run:*

| Method | Mock setup | Assert |
|--------|------------|--------|
| `recoveryJob_cancelAlreadyDone_onlyRunsRelease` | Saga `currentStep=3`; `COMPENSATION_CANCEL` step=`COMPLETED` present | `cancelOrder()` not called; `release()` called once |
| `recoveryJob_bothStepsDone_noCallsMade` | Both `COMPENSATION_CANCEL` and `COMPENSATION_RELEASE` steps=`COMPLETED` | No Feign calls made; saga set to `FAILED` |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaCompensationTest
mvn test -pl saga-orchestrator -Dtest=SagaRecoveryJobTest
```

---

### EQ-115 · Saga Settlement Failure & Manual Reconciliation
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-113c |

> **Scope:** Step 4 (SETTLEMENT_CREATE) failure handling in `saga-orchestrator` + a new `POST /ledger/credit` endpoint on `ledger-service`. No changes to order-service or the compensation steps from EQ-113c.

**Background:**
When step 4 fails, the debit has already committed — `cash_balance` is already reduced. Calling `cancelOrder()` is wrong (order is FILLED). Calling `release()` is wrong (hold was consumed by the debit; `cash_on_hold` is already reduced). Automated reversal is not safe without manual review. The correct resolution is a manual CREDIT transaction.

**Changes:**

*saga-orchestrator:*
- Add `if ("FAILED".equals(step4.getStatus()))` guard in `OrderSaga.execute()` — currently missing, causing silent fall-through to COMPLETED
- In the step 4 failure branch: set `saga.status = COMPENSATION_REQUIRED`; log CRITICAL; publish `saga.compensation.required` Kafka event with orderId, userId, amount, reason
- Do NOT call `cancelOrder()` or `release()` for step 4

*ledger-service:*
- New `POST /ledger/credit` endpoint (admin role required). Body: `{ userId, orderId, amount, reason }`. Writes a CREDIT transaction to `ledger_transactions`; increases `cash_balance`. For ops manual reconciliation only — not called by any saga code.

*audit-service:*
- New Kafka consumer for `saga.compensation.required` topic. Records the event as an audit entry.

**New Kafka Topic:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `saga.compensation.required` | `saga-orchestrator` | `audit-service` | Ops alert — manual financial reconciliation required |

**Acceptance Criteria:**
- [ ] Step 4 failure is detected; saga no longer falls through to COMPLETED
- [ ] Step 4 failure sets `saga.status = COMPENSATION_REQUIRED`; CRITICAL log line emitted
- [ ] `saga.compensation.required` Kafka event published with orderId, userId, amount, reason
- [ ] `cancelOrder()` is NOT called for step 4
- [ ] `release()` is NOT called for step 4
- [ ] `audit-service` consumes the event and writes an audit record
- [ ] `POST /ledger/credit` (admin role) writes a CREDIT transaction and increases `cash_balance`; returns HTTP 200

**Test Cases:**

| Module | Method | Assert |
|--------|--------|--------|
| `saga-orchestrator` | `settlement_fails_setsCompensationRequired` | saga=COMPENSATION_REQUIRED; no cancel/release called; Kafka event published |
| `audit-service` | `sagaCompensationRequired_recordsAuditEntry` | Audit record written with orderId + amount |
| `ledger-service` | `credit_increasesCashBalance` | `cash_balance` increased by amount; CREDIT tx written to `ledger_transactions` |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaCompensationTest#settlement_fails_setsCompensationRequired
mvn test -pl audit-service -Dtest=AuditServiceTest#sagaCompensationRequired_recordsAuditEntry
mvn test -pl ledger-service -Dtest=LedgerServiceTest#credit_increasesCashBalance
```

---

### EQ-116 · Saga Data Integrity Test Suite
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-115, EQ-113c |

**Purpose:** A Testcontainers integration test that runs each compensation scenario end-to-end and asserts the correct state in every service's database. Validates that no inconsistency survives a saga failure — the goal is to guarantee that for any failure point, all three service DBs (saga, order, ledger) are always in a coherent state.

**Why a separate ticket:**
Unit tests (Mockito) mock Feign clients and validate control flow. They cannot verify that service A's DB and service B's DB are consistent after a failure. This test suite does — it runs real services against real Postgres containers.

**Test approach:**
Each test case:
1. Seeds all three DBs (saga DB, order-service DB, ledger-service DB) with known state
2. Triggers a failure at a specific step by configuring the target service to fail (e.g. WireMock stub returning 500 for the settlement endpoint)
3. Waits for the saga to reach a terminal state
4. Queries all three DBs directly via JDBC and asserts the exact expected state in each

**Test cases:**

| Scenario | Saga DB expected | Order DB expected | Ledger DB expected |
|----------|-----------------|-------------------|-------------------|
| Step 1 fails — compliance | status=FAILED; step1=FAILED | status=PENDING (unchanged) | cash_balance unchanged; cash_on_hold unchanged; no RELEASE tx |
| Step 2 fails — matching | status=FAILED; COMPENSATION_CANCEL=COMPLETED | status=CANCELLED | cash_balance unchanged; cash_on_hold unchanged; no RELEASE tx |
| Step 3 fails — debit | status=FAILED; COMPENSATION_CANCEL + COMPENSATION_RELEASE=COMPLETED | status=CANCELLED | cash_balance unchanged; cash_on_hold restored; RELEASE tx recorded |
| Step 3 fails — release also fails | status=FAILED; COMPENSATION_CANCEL=COMPLETED; COMPENSATION_RELEASE=FAILED | status=CANCELLED | cash_balance unchanged; cash_on_hold still frozen (ops must release) |
| Step 4 fails — settlement | status=COMPENSATION_REQUIRED | status=FILLED | cash_balance reduced by debit amount; DEBIT tx recorded; no RELEASE tx |
| Duplicate release (idempotency) | — | — | RELEASE tx exists exactly once; cash_on_hold released exactly once |
| Pod restart mid-compensation | Recovery job runs; saga reaches FAILED | status=CANCELLED | cash_on_hold restored |

**Run:**
```bash
mvn verify -pl saga-orchestrator -Dtest=SagaDataIntegrityTest
```

---

### EQ-114 · Remove Redundant Synchronous Order Matching in `submitOrder`
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P1 |

**Problem:**
`OrderService.submitOrder` calls the matching engine synchronously — inside the HTTP request handler — immediately after saving the order. The saga orchestrator then independently runs compliance and calls `orderClient.triggerMatch` in step 2, re-executing matching on an order that is already filled. This means:

1. Orders can fill before compliance has run, inverting the intended compliance-first model.
2. Saga step 2 (`ORDER_MATCHING`) is effectively a no-op or redundant re-execution for already-filled orders.
3. The HTTP caller receives fill details in the immediate response, creating a false expectation of synchronous execution that is inconsistent with how STOP_LOSS orders behave.

**Required Change:**
Remove the `switch (request.getType())` block at the end of `OrderService.submitOrder` that calls `matchingEngine.executeMarketOrder` and `matchingEngine.executeLimitOrder`. The method should return the saved order in `PENDING` status. The saga owns all execution from that point — compliance first, then matching in step 2. Callers retrieve fill status by polling `GET /orders/{id}`.

**Services Affected:**

| Service | Change |
|---------|--------|
| `order-service` | Remove synchronous matching call from `submitOrder`; HTTP response returns order in `PENDING` status |
| `saga-orchestrator` | No change — step 2 `triggerMatch` already handles execution correctly |

**Database Changes:** None.

**Kafka Topics:** None.

**Acceptance Criteria:**
- [ ] `POST /orders` returns the order in `PENDING` status for MARKET and LIMIT types; no fill data in the immediate response
- [ ] The saga remains the only path that calls the matching engine for all order types
- [ ] `GET /orders/{id}` returns `FILLED` / `PARTIALLY_FILLED` status after the saga completes
- [ ] All existing `OrderService` unit tests pass with the synchronous matching block removed
- [ ] A `MARKET` order submitted during NYSE hours reaches `FILLED` status via the saga within the async execution window

---

## Sprint 2 — Unit Test Coverage Detail

> **Coverage target:** 95% JaCoCo line coverage on all service-layer and business-logic classes.
> **Excluded from measurement** (consistent across all tickets): `*Application`, `*Config`, `*SecurityConfig`, `*Repository` interfaces, `*Filter`, model/DTO/enum packages.
>
> **Enforce per service** — add the following execution to each service's `pom.xml` jacoco plugin block:
> ```xml
> <execution>
>   <id>jacoco-check</id>
>   <goals><goal>check</goal></goals>
>   <configuration>
>     <excludes>
>       <exclude>**/*Application.class</exclude>
>       <exclude>**/*Config.class</exclude>
>       <exclude>**/model/*.class</exclude>
>       <exclude>**/dto/*.class</exclude>
>       <exclude>**/*Repository.class</exclude>
>       <exclude>**/*Filter.class</exclude>
>     </excludes>
>     <rules>
>       <rule>
>         <element>BUNDLE</element>
>         <limits>
>           <limit>
>             <counter>LINE</counter>
>             <value>COVEREDRATIO</value>
>             <minimum>0.95</minimum>
>           </limit>
>         </limits>
>       </rule>
>     </rules>
>   </configuration>
> </execution>
> ```
> **Validate any service:**
> ```bash
> mvn verify -pl <service-name>
> start <service-name>/target/site/jacoco/index.html   # Windows
> open  <service-name>/target/site/jacoco/index.html   # Mac
> ```

---

### EQ-117 · Order-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 |

> **Scope:** `order-service` only. Existing `OrderServiceTest` covers basic order submission and market-hours rejection. The matching engine, order book, stop-loss evaluation, and order expiry have zero coverage.

**Classes under test:** `MatchingEngine`, `OrderBook`, `StopLossService`, `OrderExpiryService`, `MarketHoursValidator`

**Test Cases — `MatchingEngineTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `executeMarketOrder_fillsAgainstBestAsk` | Ask exists at $150; market buy submitted | Fill returned; ask removed from book; filled qty and price set |
| `executeMarketOrder_noLiquidity_returnsEmpty` | Empty order book | Empty fill returned; order status remains `OPEN` |
| `executeLimitOrder_priceMatches_fills` | Limit buy at $150; ask at $150 | Fill returned immediately |
| `executeLimitOrder_priceMiss_queues` | Limit buy at $148; ask at $150 | No fill; order queued in book |
| `partialFill_remainderQueued` | Buy 10; only 6 shares available | Fill qty=6; remaining 4 queued |

**Test Cases — `OrderBookTest` (unit):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `addBid_maintainsPriceTimePriority` | Two bids at $150, then $151 | $151 bid is best bid |
| `addAsk_maintainsPriceTimePriority` | Two asks at $152, then $151 | $151 ask is best ask |
| `cancelOrder_removesFromBook` | Order in book; cancel called | Order no longer in bid/ask queue |
| `getBestBid_emptyBook_returnsEmpty` | No bids | `Optional.empty()` |

**Test Cases — `StopLossServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `evaluateTriggers_priceAtOrBelowTrigger_triggersOrder` | `PENDING_TRIGGER` order at $170; current price $169 | Order status → `TRIGGERED`; Kafka event published |
| `evaluateTriggers_priceAboveTrigger_noAction` | `PENDING_TRIGGER` order at $170; current price $171 | No status change; no event |
| `evaluateTriggers_noPendingOrders_noAction` | No `PENDING_TRIGGER` orders for ticker | Repository queried; no save; no event |
| `evaluateTriggers_multipleOrders_allTriggered` | Two orders at $170 and $165; price $164 | Both transition to `TRIGGERED` |

**Test Cases — `OrderExpiryServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `expireOpenOrders_pastExpiry_cancels` | Limit order with `expiresAt` in the past | Status → `CANCELLED`; `orderRepository.save()` called |
| `expireOpenOrders_notExpired_noAction` | `expiresAt` in the future | No status change; no save |
| `expireOpenOrders_noOpenOrders_noOp` | Repository returns empty list | No saves; no exceptions |

```bash
mvn test -pl order-service -Dtest=MatchingEngineTest,OrderBookTest,StopLossServiceTest,OrderExpiryServiceTest
mvn verify -pl order-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl order-service` passes JaCoCo 95% line coverage check on `MatchingEngine`, `OrderBook`, `StopLossService`, `OrderExpiryService`, `MarketHoursValidator`, `OrderService`
- [ ] JaCoCo `check` execution added to `order-service/pom.xml`

---

### EQ-118 · Compliance-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope:** `compliance-service` only. Existing `ComplianceServiceTest` mocks `WashSaleService` — the wash-sale detection logic itself has zero coverage.

**Classes under test:** `WashSaleService`, `ComplianceService` (remaining paths)

**Test Cases — `WashSaleServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `isWashSale_saleLossThenRepurchaseWithin30Days_returnsViolation` | SELL AAPL at loss on day 0; BUY AAPL on day 15 | `isWashSale=true`; violation with `WASH_SALE` code returned |
| `isWashSale_saleLossThenRepurchaseAt30Days_noViolation` | SELL AAPL at loss on day 0; BUY AAPL on day 30 (boundary) | `isWashSale=false` |
| `isWashSale_saleLossThenRepurchaseAt31Days_noViolation` | Repurchase 31 days after loss sale | `isWashSale=false` |
| `isWashSale_saleAtGain_neverViolation` | SELL AAPL at gain; repurchase day 5 | `isWashSale=false` — wash-sale rule only applies to losses |
| `isWashSale_noSaleHistory_noViolation` | No prior sales for ticker | `isWashSale=false`; repository queried |
| `isWashSale_differentTicker_noViolation` | SELL AAPL at loss; BUY MSFT day 5 | `isWashSale=false` — different ticker |

**Test Cases — `ComplianceServiceTest` additions:**

| Method | Scenario | Assert |
|--------|----------|--------|
| `check_multipleViolations_allReturned` | Wash-sale AND insufficient funds both fail | `approved=false`; two violations in response |
| `check_eventPublished_onApproval` | Valid order approved | `complianceEventPublisher.publishApproved()` called once |
| `check_eventPublished_onRejection` | Order rejected | `complianceEventPublisher.publishRejected()` called once |

```bash
mvn test -pl compliance-service -Dtest=WashSaleServiceTest,ComplianceServiceTest
mvn verify -pl compliance-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl compliance-service` passes JaCoCo 95% line coverage check on `WashSaleService` and `ComplianceService`
- [ ] JaCoCo `check` execution added to `compliance-service/pom.xml`

---

### EQ-119 · Settlement-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope:** `settlement-service` only. `NyseCalendar` is well covered. `SettlementService` and `SettlementScheduler` have zero coverage.

**Classes under test:** `SettlementService`, `SettlementScheduler`

**Test Cases — `SettlementServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `createSettlement_savesWithPendingStatusAndT1Date` | Valid settlement request | `settlementRepository.save()` called; status=`PENDING_SETTLEMENT`; `settlementDate` = T+1 business day |
| `processSettlements_pendingOnSettlementDate_marksSettled` | `PENDING_SETTLEMENT` record with today's settlement date | Status → `SETTLED`; `settlementEventPublisher.publish()` called |
| `processSettlements_pendingFutureDate_noAction` | Settlement date is tomorrow | No status change; no event published |
| `processSettlements_noRecords_noOp` | Repository returns empty list | No saves; no exceptions |
| `createSettlement_assignsCorrectSettlementDate_skipWeekend` | Order filled on Friday | `settlementDate` is the following Monday |

**Test Cases — `SettlementSchedulerTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `runSettlement_delegatesToService` | Scheduler method invoked | `settlementService.processSettlements()` called exactly once |

```bash
mvn test -pl settlement-service -Dtest=SettlementServiceTest,SettlementSchedulerTest
mvn verify -pl settlement-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl settlement-service` passes JaCoCo 95% line coverage check on `SettlementService`, `SettlementScheduler`, `NyseCalendar`
- [ ] JaCoCo `check` execution added to `settlement-service/pom.xml`

---

### EQ-120 · Auth-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope:** `auth-service` only. Existing `AuthServiceTest` covers `AuthService.issueToken()` and `JwtService` token generation/validation. `JwtAuthFilter` and `RoleConverter` have zero coverage.

**Classes under test:** `JwtAuthFilter`, `RoleConverter`

**Test Cases — `JwtAuthFilterTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `doFilter_validBearerToken_setsSecurityContext` | Request with valid `Authorization: Bearer <token>` | `SecurityContextHolder` populated; `filterChain.doFilter()` called |
| `doFilter_missingAuthHeader_proceeds_unauthenticated` | No `Authorization` header | Filter does not throw; `filterChain.doFilter()` called; security context empty |
| `doFilter_expiredToken_proceeds_unauthenticated` | `Authorization` header with expired JWT | Security context not populated; chain proceeds |
| `doFilter_malformedToken_proceeds_unauthenticated` | `Authorization: Bearer not-a-jwt` | No exception propagated; chain proceeds |

**Test Cases — `RoleConverterTest` (unit):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `convertToDatabaseColumn_allRoles` | Each `Role` enum value passed | Returns correct string representation |
| `convertToEntityAttribute_allValues` | Each string value passed | Returns correct `Role` enum constant |
| `convertToEntityAttribute_unknownValue_throws` | Unrecognised string | `IllegalArgumentException` thrown |

```bash
mvn test -pl auth-service -Dtest=JwtAuthFilterTest,RoleConverterTest
mvn verify -pl auth-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl auth-service` passes JaCoCo 95% line coverage check on `AuthService`, `JwtService`, `JwtAuthFilter`, `RoleConverter`
- [ ] JaCoCo `check` execution added to `auth-service/pom.xml`

---

### EQ-121 · Audit-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 1 | P0 |

> **Scope:** `audit-service` only. Existing `AuditServiceTest` covers `AuditService.logEvent()` and `getEventsByOrder()`. `AuditEventListener` (Kafka consumer) has zero coverage.

**Classes under test:** `AuditEventListener`

**Test Cases — `AuditEventListenerTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `onEvent_orderPlaced_logsAuditEvent` | `ORDER_PLACED` Kafka payload received | `auditService.logEvent()` called with matching `eventType` and `orderId` |
| `onEvent_sagaStarted_logsAuditEvent` | `SAGA_STARTED` payload received | `auditService.logEvent()` called; `sagaId` present in payload |
| `onEvent_sagaFailed_logsAuditEvent` | `SAGA_FAILED` payload received | `auditService.logEvent()` called; `failureReason` preserved |
| `onEvent_nullPayload_doesNotThrow` | Null message body | No exception; `logEvent()` not called |

```bash
mvn test -pl audit-service -Dtest=AuditEventListenerTest
mvn verify -pl audit-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl audit-service` passes JaCoCo 95% line coverage check on `AuditService` and `AuditEventListener`
- [ ] JaCoCo `check` execution added to `audit-service/pom.xml`

---

### EQ-122 · Saga-Orchestrator Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope:** `saga-orchestrator` only. `SagaController`, `SagaEventListener`, and `GlobalExceptionHandler` have zero coverage.

**Classes under test:** `SagaController`, `SagaEventListener`, `GlobalExceptionHandler`

**Test Cases — `SagaControllerTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `getSaga_found_returns200` | Valid `sagaId` exists | HTTP 200; saga body returned |
| `getSaga_notFound_returns404` | `sagaId` does not exist | HTTP 404 |
| `getSagaByOrderId_found_returns200` | Valid `orderId` with associated saga | HTTP 200; saga body returned |
| `getSagaByOrderId_notFound_returns404` | No saga for `orderId` | HTTP 404 |
| `getSagasByStatus_returns200` | Query by `COMPENSATING` status | HTTP 200; list of matching sagas returned |

**Test Cases — `SagaEventListenerTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `onOrderPlaced_startsSaga` | `ORDER_PLACED` Kafka event received | `sagaService.startSaga()` called with correct `orderId` and `userId` |
| `onStopLossTriggered_reEntersSaga` | `STOP_LOSS_TRIGGERED` event; existing saga found | `orderSaga.execute()` called; order type overridden to `MARKET` |
| `onStopLossTriggered_sagaNotFound_logs` | No saga exists for `orderId` | No exception; error logged |

**Test Cases — `GlobalExceptionHandlerTest` (unit):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `handleUnknownException_returns500` | Unhandled `RuntimeException` thrown | HTTP 500; body contains error message |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaControllerTest,SagaEventListenerTest,GlobalExceptionHandlerTest
mvn verify -pl saga-orchestrator
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl saga-orchestrator` passes JaCoCo 95% line coverage check on `SagaController`, `SagaEventListener`, `SagaService`, `OrderSaga`, `GlobalExceptionHandler`
- [ ] JaCoCo `check` execution added to `saga-orchestrator/pom.xml`

---

### EQ-123 · Market-Data-Service Unit Test Coverage
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 5 | P0 |

> **Scope:** `market-data-service` only. **Zero tests exist.** This is the only service with no test directory. A full suite must be created from scratch covering `MarketDataService`, `ScenarioEngine`, and `StopLossTriggerService`.

**Classes under test:** `MarketDataService`, `ScenarioEngine`, `StopLossTriggerService`

**Test Cases — `MarketDataServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `getCurrentPrice_found_returnsPrice` | Ticker exists in repository | Returns `TickerPrice` with correct price |
| `getCurrentPrice_notFound_throws` | Ticker not in repository | `EntityNotFoundException` (or equivalent) thrown |
| `updatePrice_savesUpdatedRecord` | New price submitted for existing ticker | `tickerPriceRepository.save()` called with new price value |
| `getAllPrices_returnsList` | Multiple tickers seeded | Returns full list from repository |

**Test Cases — `ScenarioEngineTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `startScenario_savesActiveSession` | `flash_crash` scenario started | Scenario session saved; initial price deltas applied to all affected tickers |
| `stopScenario_clearsActiveSession` | Active scenario stopped | Session removed; no further price steps applied |
| `applyStep_adjustsPricesByScenarioDelta` | One step of `bull_run` scenario applied | Each ticker price updated by the configured delta; `save()` called per ticker |
| `startScenario_unknownType_throws` | Unknown scenario name passed | `IllegalArgumentException` thrown |
| `applyStep_noActiveScenario_noOp` | No active scenario when step fires | No price changes; no repository writes |

**Test Cases — `StopLossTriggerServiceTest` (unit, Mockito):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `evaluate_priceAtOrBelowTrigger_callsOrderService` | Current price $149; `PENDING_TRIGGER` order at $150 | `orderServiceClient.evaluateStopLoss()` called with correct ticker and price |
| `evaluate_priceAboveTrigger_noCall` | Current price $151; trigger at $150 | `orderServiceClient` not called |
| `evaluate_orderServiceDown_logsAndContinues` | `orderServiceClient` throws `FeignException` | Exception caught; error logged; no rethrow |

```bash
mvn test -pl market-data-service -Dtest=MarketDataServiceTest,ScenarioEngineTest,StopLossTriggerServiceTest
mvn verify -pl market-data-service
```

**Acceptance Criteria:**
- [ ] Test directory created at `market-data-service/src/test/java/com/equiflow/marketdata/`
- [ ] All test cases above pass
- [ ] `mvn verify -pl market-data-service` passes JaCoCo 95% line coverage check on `MarketDataService`, `ScenarioEngine`, `StopLossTriggerService`
- [ ] JaCoCo `check` execution added to `market-data-service/pom.xml`

---

### EQ-124 · Ledger-Service Coverage Completion
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 1 | P0 |

> **Scope:** `ledger-service` only. `LedgerService` is well covered by existing unit and Testcontainers tests. `LedgerController` REST layer has zero coverage. This ticket adds controller tests and enforces the JaCoCo rule.

**Classes under test:** `LedgerController`

**Test Cases — `LedgerControllerTest` (unit, Mockito + MockMvc):**

| Method | Scenario | Assert |
|--------|----------|--------|
| `hold_validRequest_returns200` | `POST /ledger/hold` with valid body | HTTP 200; `ledgerService.hold()` called once |
| `release_validRequest_returns200` | `POST /ledger/release` with valid body | HTTP 200; `ledgerService.release()` called once |
| `debit_validRequest_returns200` | `POST /ledger/debit` with valid body | HTTP 200; `ledgerService.debit()` called once |
| `getAccount_found_returns200` | `GET /ledger/accounts/{userId}` for existing user | HTTP 200; account body returned |
| `getAccount_notFound_returns404` | `GET /ledger/accounts/{userId}` for unknown user | HTTP 404 |
| `hold_insufficientFunds_returns400` | `ledgerService.hold()` throws `InsufficientFundsException` | HTTP 400; error message in body |

```bash
mvn test -pl ledger-service -Dtest=LedgerControllerTest
mvn verify -pl ledger-service
```

**Acceptance Criteria:**
- [ ] All test cases above pass
- [ ] `mvn verify -pl ledger-service` passes JaCoCo 95% line coverage check on `LedgerService` and `LedgerController`
- [ ] JaCoCo `check` execution added to `ledger-service/pom.xml` (supplement existing jacoco config)

---

## Backlog — Approved, Not Yet Scheduled

---

### EQ-202 · Account Funding — Deposit and Withdrawal
| Epic | Type | Points |
|------|------|--------|
| Account Management | Feature | 8 |

**Product Request:**
> "Users can't do anything useful until they can fund their account. We need
> deposit and withdrawal flows — even if the actual bank integration is mocked
> for now, the API and ledger side must be fully implemented."

**Functionality:**
New deposit and withdrawal endpoints on `ledger-service` allow users to move
funds into and out of their account. The actual ACH/bank transfer is stubbed
with a mock that always returns success. All funding events are published to
Kafka and logged by `audit-service`. The ledger records each transaction as an
immutable entry for reconciliation.

**Services Affected:**

| Service | Change |
|---------|--------|
| `ledger-service` | New `POST /accounts/deposit` and `POST /accounts/withdraw` endpoints; update `available_balance`; write to `funding_transactions` table |
| `audit-service` | Consumes `account.funded` and `account.withdrawn` events |
| `api-gateway` | Routes already handle `/accounts/**`; verify deposit/withdraw paths are included |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `ledger-service` | New `funding_transactions` table: `id`, `user_id`, `type` (`DEPOSIT`/`WITHDRAWAL`), `amount`, `status`, `reference_id`, `created_at`; update `accounts` table `available_balance` column atomically |

**Kafka Topics:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `account.funded` | `ledger-service` | `audit-service` | Records a successful deposit |
| `account.withdrawn` | `ledger-service` | `audit-service` | Records a successful withdrawal |

**Happy Path:**
1. User calls `POST /accounts/deposit` with `{ amount: 5000.00 }`
2. `ledger-service` validates the amount, calls the stubbed bank integration (always succeeds)
3. `available_balance` is increased atomically; a `funding_transactions` record is written
4. `account.funded` Kafka event is published; `audit-service` logs it
5. Response returns the new available balance and transaction ID

**Edge Cases:**
- Deposit amount is zero or negative → HTTP 400
- Withdrawal amount exceeds `available_balance` → HTTP 422 with current balance in the response
- Withdrawal is requested while a hold is active → only free balance (available minus held) is withdrawable; held funds are not withdrawable
- Concurrent deposit and withdrawal on the same account → `UPDATE accounts SET available_balance = available_balance + ?` must use atomic SQL; no lost update
- Stubbed bank integration returns an error → transaction is not created; balance is not modified; HTTP 502 returned

**Acceptance Criteria:**
- [ ] `POST /accounts/deposit` increases `available_balance` and creates a `funding_transactions` record
- [ ] `POST /accounts/withdraw` decreases `available_balance` if sufficient free funds exist
- [ ] Withdrawal below available free balance returns HTTP 422 with current balance in the error body
- [ ] All deposit and withdrawal events are published to Kafka and logged by `AuditService`
- [ ] Bank integration is stubbed; real ACH integration is out of scope

