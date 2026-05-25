# EquiFlow — Product & Engineering Backlog
**Version:** 1.3
**Status:** Approved
**Product Owner:** Claude
**Engineering Lead:** Michael Montgomery
**Last Updated:** 2026-05-22

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
| ⚪ | <nobr>[EQ-113b](#eq-113b--target-service-idempotency)</nobr> | Target Service Idempotency — system-cancel endpoint on order-service; release() idempotency on ledger-service | 2 | P0 — can start parallel with EQ-113a |
| ⚪ | <nobr>[EQ-113c](#eq-113c--saga-compensation-wiring--recovery-job)</nobr> | Saga Compensation Wiring + Recovery — cancel/release steps in failSaga(); SagaStep recording; SagaRecoveryJob | 3 | P0 — depends on EQ-113a, EQ-113b |
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

> **Scope:** `saga-orchestrator` only. Wires cancel + release Feign calls into `failSaga()`, records SagaStep outcomes, and adds the scheduled recovery job. Depends on EQ-113a (COMPENSATING checkpoint already in place) and EQ-113b (target services are idempotent).

**Changes:**

1. **`OrderClient`** — add `cancelOrder(orderId, userId)` Feign method calling `POST /orders/{orderId}/system-cancel`
2. **`failSaga()` compensation steps** — after writing COMPENSATING (EQ-113a), run per failed step:
   - Step 1 (compliance): no Feign calls. Set saga → FAILED.
   - Step 2 (matching): call `cancelOrder()`. Record `COMPENSATION_CANCEL` SagaStep. Set saga → FAILED.
   - Step 3 (debit): call `cancelOrder()` then `release()` independently — each in its own try/catch so one failure does not block the other. Record both SagaSteps. Set saga → FAILED.
3. **SagaStep recording** — write `COMPENSATION_CANCEL` or `COMPENSATION_RELEASE` SagaStep with status=COMPLETED or FAILED + error message on failure. Before each call, check if a COMPLETED SagaStep already exists for that step — skip the call if so.
4. **`SagaRecoveryJob`** — `@Scheduled` every 60s. Queries sagas in COMPENSATING state older than 2 minutes. For each: reads existing SagaSteps, skips already-COMPLETED steps, re-runs missing ones via the same compensation entry point as normal `failSaga()` flow.
5. **Logging** — all compensation log lines include: orderId, userId, step, outcome, reason on failure.

**What applies per failed step:**

| Step failed | Cancel Order | Release Hold |
|-------------|-------------|-------------|
| Step 1 — Compliance | No | No |
| Step 2 — Matching | Yes | No |
| Step 3 — Debit | Yes | Yes |

**Logging format:**
```
INFO  saga_compensation step=CANCEL orderId={} userId={} outcome=COMPLETED
ERROR saga_compensation step=RELEASE orderId={} userId={} amount={} outcome=FAILED reason={}
WARN  saga_compensation_anomaly step=CANCEL orderId={} userId={} orderStatus=FILLED
```

**Services Affected:** `saga-orchestrator` only.

**Acceptance Criteria:**
- [ ] Step 1 failure: no Feign calls made; saga → FAILED; no COMPENSATION SagaSteps written
- [ ] Step 2 failure: `cancelOrder()` called once; `COMPENSATION_CANCEL` SagaStep written; saga → FAILED
- [ ] Step 3 failure: `cancelOrder()` and `release()` both called independently; both SagaSteps written; saga → FAILED
- [ ] If cancel Feign call fails, release still runs; `COMPENSATION_CANCEL` SagaStep written with status=FAILED; saga reaches FAILED
- [ ] If release Feign call fails, `COMPENSATION_RELEASE` SagaStep written with status=FAILED; saga reaches FAILED
- [ ] Compensation step is skipped if its SagaStep already exists with status=COMPLETED
- [ ] `SagaRecoveryJob` runs every 60 seconds; skips sagas in COMPENSATING state newer than 2 minutes
- [ ] Recovery job re-runs only missing compensation steps; skips completed ones
- [ ] All compensation log lines include orderId, userId, step, outcome, reason

**Test Cases — `SagaCompensationTest` (unit, Mockito):**

| Scenario | Mock setup | Assert |
|----------|------------|--------|
| Step 1 fails | `complianceClient.check()` throws | saga=FAILED; no cancel/release called; no COMPENSATION SagaSteps |
| Step 2 fails | `orderClient.triggerMatch()` throws | saga=FAILED; `cancelOrder()` called once; COMPENSATION_CANCEL step=COMPLETED |
| Step 3 fails | `ledgerClient.debit()` throws | saga=FAILED; cancel + release both called; both steps recorded |
| Cancel Feign fails, release succeeds | `cancelOrder()` throws | saga=FAILED; COMPENSATION_CANCEL step=FAILED; COMPENSATION_RELEASE step=COMPLETED |
| Release Feign fails, cancel succeeds | `release()` throws | saga=FAILED; COMPENSATION_CANCEL step=COMPLETED; COMPENSATION_RELEASE step=FAILED |
| COMPENSATION_CANCEL already COMPLETED | Pre-existing SagaStep present | `cancelOrder()` not called; release() still runs |

**Test Cases — `SagaRecoveryJobTest` (unit, Mockito):**

| Scenario | Assert |
|----------|--------|
| No COMPENSATING sagas | No compensation called |
| Saga in COMPENSATING < 2 min | Skipped — not yet eligible |
| Saga in COMPENSATING > 2 min, no steps done | Compensation entry point called for all steps |
| Saga in COMPENSATING > 2 min, cancel done | Cancel skipped; release called |

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

## Backlog — AI Agents

Each agent builds on the existing `run_agent()` loop in `equiflow-mcp/loop.py` and the handler registry in `equiflow_data_server.py`. The loop itself does not change — only the system prompt, tools, and trigger wiring differ.

| Status | Ticket | Task | Points | Priority |
|--------|--------|------|--------|----------|
| ✅ | <nobr>[EQ-130](#eq-130--on-demand-compliance-breach-summary-agent)</nobr> | On-Demand Compliance Breach Summary Agent | 3 | P1 |
| ⚪ | <nobr>[EQ-133](#eq-133--agent-test-suite-compliance-agent)</nobr> | Agent Test Suite — Compliance Agent | 3 | P1 — depends on EQ-130 |
| ~~⚪~~ | ~~EQ-131 · Scheduled EOD Settlement Reconciliation Agent~~ | ~~removed — problem already solved by existing `/settlement/pending` endpoint~~ | ~~5~~ | ~~P1~~ |
| ⚪ | <nobr>[EQ-134](#eq-134--agent-test-suite-settlement-reconciliation-agent)</nobr> | Agent Test Suite — Settlement Reconciliation Agent | 3 | P1 — reserved for future settlement agent |
| ⚪ | <nobr>[EQ-132](#eq-132--triggered-order-failure-escalation-agent)</nobr> | Triggered Order Failure Escalation Agent | 5 | P1 |
| ⚪ | <nobr>[EQ-135](#eq-135--agent-test-suite-order-failure-escalation-agent)</nobr> | Agent Test Suite — Order Failure Escalation Agent | 3 | P1 — depends on EQ-132 |
| ✅ | <nobr>[EQ-136](#eq-136--duplicate-order-detection-agent)</nobr> | Duplicate Order Detection Agent | 5 | P1 |
| ✅ | <nobr>[EQ-137](#eq-137--agent-visualization-frontend)</nobr> | Agent Visualization Frontend — React + FastAPI SSE live step timeline | 5 | P1 |

---

### EQ-130 · On-Demand: Compliance Breach Summary Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Feature | 3 | P1 |

**Purpose:** Summarizes all wash-sale and insufficient-funds violations for a given period and flags accounts with repeat breaches.

**Invocation pattern:** On-demand — a human runs it from the CLI.

**Problem it solves:** Compliance officers today manually query rejected orders, cross-reference the compliance check results for each, and group by failure type. This takes 15–30 minutes each morning. This agent does it in one command.

**Example usage:**
```bash
python equiflow-mcp/compliance_agent.py "Show me today's compliance breaches"
python equiflow-mcp/compliance_agent.py "Which accounts have repeated wash-sale violations this week?"
```

**Tools required:**

| Tool | Status | Notes |
|------|--------|-------|
| `list_orders` | Existing | Filter by `status=REJECTED` and date range |
| `get_order` | Existing | Retrieve saga ID per rejected order |
| `get_compliance_result` | **New** | `GET /compliance/results/order/{orderId}` — returns failure reason, violation type, check timestamp |

**New endpoint needed:**

`GET /compliance/results/order/{orderId}` on `compliance-service`
- Returns: `{ orderId, violationType: "WASH_SALE" | "INSUFFICIENT_FUNDS", failureReason, checkedAt }`
- Auth: BOT_OPERATOR role

**System prompt:**
```
You are an EquiFlow compliance monitoring agent.

Your goal: summarise all compliance breaches for the requested time period
so a compliance officer can review them in one read.

Your final response must include:
- Total breach count
- Breakdown by violation type (WASH_SALE vs INSUFFICIENT_FUNDS)
- Which accounts appear more than once (repeat offenders)
- The most recent breach per account

Do not include order IDs unless the user asks for them.
Do not speculate on why a breach occurred beyond what the data shows.
```

**Branching logic:**
```
list_orders(status=REJECTED, from=today)
  → for each order: get_compliance_result(order_id)
    → group by violationType
    → identify repeat accounts
  → summarise
```

**File to create:** `equiflow-mcp/compliance_agent.py`

---


### EQ-132 · Triggered: Order Failure Escalation Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Feature | 5 | P1 |

**Purpose:** Diagnoses the root cause of every FAILED order and decides whether to recommend a retry, flag for investigation, or create a PagerDuty incident.

**Problem it solves:** When a saga fails, ops receives a Kafka event with an order ID and nothing else. They manually trace the saga, audit log, and ledger to understand root cause and decide whether to retry, credit the account, or escalate. This agent does that trace automatically and emits a structured decision.

---

### Invocation Modes

This agent supports two invocation modes — same decision logic, different trigger:

| Mode | How | Used for |
|---|---|---|
| **Kafka-triggered** | `kafka_consumer.py` fires when `equiflow.saga.failed` or `equiflow.saga.compensated` event arrives; passes `order_id` as CLI arg | Production background operation |
| **On-demand (UI)** | User selects agent in the React frontend and types a question | Demo / manual investigation |

**UI example prompts:**
- `Escalate any failed orders from the last hour`
- `Investigate failed orders today`

**Kafka wiring** (`equiflow-mcp/kafka_consumer.py`):
```python
from kafka import KafkaConsumer
import subprocess, json

consumer = KafkaConsumer(
    "equiflow.saga.failed",
    "equiflow.saga.compensated",
    bootstrap_servers="localhost:9092",
    value_deserializer=lambda m: json.loads(m.decode("utf-8")),
)

for message in consumer:
    order_id = message.value["orderId"]
    subprocess.run(["python", "equiflow-mcp/escalation_agent.py", order_id])
```

---

### Tools

| Tool | Status | Purpose |
|------|--------|---------|
| `list_orders` | Existing | On-demand mode: scan for FAILED orders in time window |
| `get_order` | Existing | Confirm failure status and retrieve saga ID and user ID |
| `get_saga` | Existing | Identify which step failed, failure reason, saga status, retry count |
| `query_audit_log` | Existing | Full retry history and timestamps — used when saga status is COMPENSATING |
| `get_ledger_account` | **New** | `GET /ledger/accounts/{userId}` — check if insufficient funds failure is still blocking. Handler added to `equiflow_data_server.py`. |
| `create_incident` | **New** | Mock PagerDuty — returns a fake incident ID. Handler added to `equiflow_data_server.py`. In production this would call the real PagerDuty Events API. |

**`get_ledger_account` handler:**
```python
async def handle_get_ledger_account(args: dict) -> list[TextContent]:
    return await _ok(await authed_get(f"/ledger/accounts/{args['user_id']}"))
```

**`create_incident` handler (mock):**
```python
import uuid as _uuid

async def handle_create_incident(args: dict) -> list[TextContent]:
    incident_id = f"PD-{str(_uuid.uuid4())[:8].upper()}"
    payload = {
        "incident_id": incident_id,
        "order_id": args["order_id"],
        "severity": args.get("severity", "HIGH"),
        "reason": args["reason"],
        "status": "triggered",
        "note": "Mock incident — in production this calls PagerDuty Events API v2",
    }
    return [TextContent(type="text", text=json.dumps(payload))]
```

---

### Decision Rules

| Condition | Action | Incident? |
|---|---|---|
| `failure_reason` in `[TIMEOUT, NETWORK_ERROR]` AND `retry_count < 3` | RETRY — transient; recommend automatic retry | No |
| `failure_reason` in `[TIMEOUT, NETWORK_ERROR]` AND `retry_count >= 3` | ESCALATE — retries exhausted | Yes |
| `failure_reason` in `[COMPLIANCE_FAILED, REJECTED]` | NO_ACTION — expected rejection, not a system failure | No |
| `failure_reason == INSUFFICIENT_FUNDS` AND `availableCash >= required` | INVESTIGATE — balance now sufficient; recommend manual settlement retry | No |
| `failure_reason == INSUFFICIENT_FUNDS` AND `availableCash < required` | ESCALATE — account holder must be contacted | Yes |
| `saga.status == COMPENSATING` | ESCALATE — always; financial reconciliation required regardless of failure reason | Yes |

> **Note on saga status:** `COMPENSATING` (not `COMPENSATION_REQUIRED`) is the correct saga status. It means the orchestrator is actively rolling back a partial transaction. Check `saga.status`, not order status.

---

### Branching Logic

```
# On-demand mode: scan for failures
list_orders(status=FAILED, from=<window>)
  → for each order:
      → get_order(order_id)           # get saga_id, user_id
      → get_saga(saga_id)             # get failed_step, failure_reason, retry_count, saga.status

# Both modes: decide per order
if saga.status == COMPENSATING:
    → query_audit_log(order_id)       # get full context
    → create_incident(order_id, reason="Saga compensation required — manual reconciliation needed", severity="CRITICAL")

elif failure_reason in [TIMEOUT, NETWORK_ERROR]:
    if retry_count < 3:
        → recommend RETRY             # no tool call needed
    else:
        → create_incident(order_id, reason="Retry limit reached", severity="HIGH")

elif failure_reason in [COMPLIANCE_FAILED, REJECTED]:
    → NO_ACTION                       # expected; not a system failure

elif failure_reason == INSUFFICIENT_FUNDS:
    → get_ledger_account(user_id)     # check current balance
    if availableCash >= required:
        → recommend INVESTIGATE       # manual settlement retry
    else:
        → create_incident(order_id, reason="Account balance insufficient", severity="HIGH")
```

---

### System Prompt

```
You are an EquiFlow order failure escalation agent. Today is {today}.

You may be invoked in two ways:
1. A specific order ID is provided — investigate that order immediately using get_order.
2. A time window question is asked — use list_orders(status=FAILED) to find all failures first.

In both cases, apply the same decision rules per order:

DECISION RULES:
- saga.status == COMPENSATING → always create_incident (financial reconciliation required)
- failure_reason in [TIMEOUT, NETWORK_ERROR] and retry_count < 3 → recommend RETRY
- failure_reason in [TIMEOUT, NETWORK_ERROR] and retry_count >= 3 → create_incident (retries exhausted)
- failure_reason in [COMPLIANCE_FAILED, REJECTED] → NO_ACTION (expected rejection)
- failure_reason == INSUFFICIENT_FUNDS → call get_ledger_account to check current balance
    if balance sufficient → recommend INVESTIGATE (manual retry)
    if balance insufficient → create_incident (contact account holder)

Your final response must state for each order:
- Root cause (exact failure_reason from the data)
- Action taken (RETRY / INVESTIGATE / ESCALATE / NO_ACTION)
- Why

Do not speculate beyond what the tools return. If data is missing, say so.

End your reply with this block (valid JSON, tags unchanged):

<findings_json>
{{
  "mode": "<triggered|scan>",
  "window": {{"from": "<ISO>", "to": "<ISO>"}},
  "total_investigated": <int>,
  "verdicts": [
    {{
      "order_id": "<UUID>",
      "user_id": "<userId>",
      "ticker": "<ticker>",
      "failed_step": "<step_name>",
      "failure_reason": "<exact string>",
      "saga_status": "<FAILED|COMPENSATING|COMPENSATED>",
      "retry_count": <int>,
      "action": "<RETRY|INVESTIGATE|ESCALATE|NO_ACTION>",
      "incident_id": "<PD-XXXX or null>",
      "explanation": "<one sentence>"
    }}
  ],
  "verdict": "<ALL_CLEAR|ESCALATE>"
}}
</findings_json>

ALL_CLEAR if all actions are RETRY, INVESTIGATE, or NO_ACTION. ESCALATE if any incident was created.
```

---

### Files

| File | Action |
|------|--------|
| `equiflow-mcp/escalation_agent.py` | **Create** — system prompt, tools, dispatch, CLI entry |
| `equiflow-mcp/kafka_consumer.py` | **Create** — Kafka consumer wiring for background trigger mode |
| `equiflow-mcp/equiflow_data_server.py` | **Modify** — add `handle_get_ledger_account` and `handle_create_incident` handlers |
| `equiflow-mcp/api.py` | **Modify** — add `escalation` to AGENTS dict and TRIAGE_DISPATCH (or new ESCALATION_DISPATCH) |
| `frontend/src/components/AgentRunner.tsx` | **Modify** — set `ready: true`, add placeholder and examples |

### Acceptance Criteria

- [ ] Agent correctly identifies and creates incidents for COMPENSATING sagas
- [ ] Agent recommends RETRY for transient errors (retry_count < 3), escalates when retries exhausted
- [ ] Agent makes NO_ACTION for COMPLIANCE_FAILED / REJECTED — does not create unnecessary incidents
- [ ] INSUFFICIENT_FUNDS branch calls get_ledger_account and decides based on current balance
- [ ] `<findings_json>` block present and valid JSON in every response
- [ ] Works in both Kafka-triggered (CLI arg) and on-demand (UI) modes
- [ ] LIVE and LOCAL modes functional in UI; LOCAL uses rule-based planner (EQ-139) covering all decision branches

---

---

---

## Agent Testing Architecture

Agentic code has two failure modes standard unit tests don't catch: **loop failures** (wrong message order, missed stop reason, runaway iterations) and **behavioral failures** (wrong tool call sequence, missing required output sections, bad decision branches). The three-tier test architecture covers both.

**Testing pyramid:**

```
        ▲
       /T3\        End-to-end / Golden   ~5%    real model, real data, run manually
      /----\
     / T2   \      Behavioral             ~20%   mock LLM + HTTP, run on every PR
    /--------\
   /    T1    \    Unit                   ~75%   mock everything, run on every commit
  /____________\
```

| | Tier 1 — Unit | Tier 2 — Behavioral | Tier 3 — Golden |
|---|---|---|---|
| **Traditional equivalent** | `OrderServiceTest` — one class, mocked deps | `@WebMvcTest` — slice wired, external I/O mocked | Testcontainers — real Postgres + Kafka |
| **What's mocked** | Anthropic API, httpx, everything | Anthropic API + HTTP to services | Nothing — golden file replays saved model responses |
| **What runs real** | Loop logic, handler URL building | Loop, dispatch, handler logic | Loop, dispatch, handlers, real services, real DB |
| **LLM called?** | No | No | No (golden file replay) |
| **Speed** | <1 ms | <100 ms | 10–30 s |
| **Cost** | Free | Free | API credits on record; free on replay |
| **Runs in CI?** | Yes | Yes | No |
| **When to run** | Every commit | Every PR | Before prompt changes, model upgrades, releases |
| **What it catches** | Loop bugs, URL construction, error handling | Wrong tool sequence, missing output sections, bad branches | Real model regressions, stale tool return shapes |

**How the golden file works (Tier 3):**
The Anthropic API is called once during recording. Every model response — including tool calls and the final answer — is saved to a JSON fixture. On replay, a mock intercepts `client.messages.create()` and returns the saved response instead of calling the network. The loop code runs for real against real services; only the model is frozen. This removes LLM non-determinism from test runs while still validating the full stack.

**How golden files go stale:** system prompt changes, model upgrade, new tools added to the server, or seed data changes. When any of these happen, re-run `record_golden.py` and commit the updated fixture.

**Assertion rule for Tier 2 + 3:** assert facts, not exact strings.
```python
assert "3" in answer                 # count present — survives rephrasing
assert "WASH_SALE" in answer         # violation type named
assert "repeat" in answer.lower()    # concept present
# NOT: assert answer == "3 breaches detected this week"
```

**Shared test files across all three agents:**

```
equiflow-mcp/
  tests/
    __init__.py
    fixtures/                             # JSON fixtures for Tier 2 + Tier 3 golden files
    test_loop.py                          # Tier 1 — shared loop mechanics (all agents)
    test_handlers.py                      # Tier 1 — handler URL + error logic (grows per agent)
    test_compliance_agent_behavior.py     # Tier 2 — EQ-133
    test_settlement_agent_behavior.py     # Tier 2 — EQ-134
    test_escalation_agent_behavior.py     # Tier 2 — EQ-135
    record_golden.py                      # Tier 3 — records a real conversation to fixture
    test_compliance_agent_e2e.py          # Tier 3 — replay golden, assert facts (EQ-133)
  requirements-test.txt
```

**Run all tests:**
```bash
cd equiflow-mcp
pip install -r requirements-test.txt
pytest tests/ -v
```

---

### EQ-133 · Agent Test Suite — Compliance Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Engineering | 3 | P1 — depends on EQ-130 |

**Problem:**
Agentic code has two failure modes standard unit tests do not catch: loop failures (wrong message building, missed stop reasons, runaway iterations) and behavioral failures (agent calls the wrong tools, misses required output sections, or hallucinates beyond the data). This ticket adds a two-tier test suite that covers both, plus documents the Tier 3 golden file approach.

**Test architecture:**

```
Tier 1: Unit tests       — mock Anthropic + mock HTTP, pure logic
Tier 2: Behavioral tests — fixed mock LLM responses, assert output facts
Tier 3: Golden replay    — record real conversation once, replay deterministically (manual only)
```

---

**Tier 1 — Unit Tests**

**`tests/test_loop.py`** — tests `run_agent()` in isolation. Mocks `anthropic.Anthropic`. No real API calls.

| Test | Scenario | Assert |
|------|----------|--------|
| `test_end_turn_returns_text` | Model returns `end_turn` immediately | Loop exits after 1 iteration; correct text returned |
| `test_single_tool_call_then_end_turn` | Model returns `tool_use`, then `end_turn` | Tool called once with correct args; final text returned |
| `test_parallel_tool_calls_collected` | Model returns two tool blocks in one response | Both tools called before next model turn; results collected in one turn |
| `test_max_iterations_returns_error` | Model never stops (`tool_use` every turn) | Loop exits after cap; returns iteration-limit error string |
| `test_max_tokens_returns_error` | `stop_reason=max_tokens` | Returns specific max-tokens error string; no exception |
| `test_unknown_tool_returns_error_string` | Tool name not in dispatch | `"Unknown tool: x"` returned as tool result; loop continues |

**`tests/test_handlers.py`** — tests each handler in `equiflow_data_server.py`. Mocks `httpx`. No real HTTP calls.

| Test | Scenario | Assert |
|------|----------|--------|
| `test_list_orders_no_filters` | Empty args dict | Calls `GET /orders/internal/all` with no query string |
| `test_list_orders_with_filters` | `status=REJECTED`, `from=2026-05-20` | URL contains `?status=REJECTED&from=2026-05-20` |
| `test_list_orders_http_error` | Server returns 500 | Returns `"Error 500: ..."` text; no exception raised |
| `test_get_compliance_result_success` | 200 response with violation JSON | Returns response text unchanged |
| `test_get_compliance_result_404` | Server returns 404 | Returns `"Error 404: ..."` text; no exception |
| `test_token_fetched_on_first_call` | `_token` is None | `get_token()` called; request includes `Authorization: Bearer` header |
| `test_token_refreshed_on_401` | First request returns 401 | Token refreshed; request retried exactly once |

---

**Tier 2 — Behavioral Tests**

**`tests/test_compliance_agent_behavior.py`** — mocks both Anthropic and HTTP. Feeds the agent scripted LLM responses and scripted tool returns. Runs real `loop.py` and `compliance_agent.py` dispatch code. Asserts output **facts**, not exact strings.

**Key principle:** assert the data point is present, not the exact wording. The model may phrase "3 breaches detected" or "Total: 3" — both are correct. Asserting `"3" in answer` survives prompt changes and model upgrades.

```python
# Example — not exact wording, but required facts
assert "3" in answer                    # breach count present
assert "WASH_SALE" in answer            # violation type named
assert "INSUFFICIENT_FUNDS" in answer   # other type named
assert "repeat" in answer.lower()       # repeat offender concept mentioned
mock_call_tool.assert_called_with(      # correct tool args
    "list_orders", {"status": "REJECTED", "from": "2026-05-20", "to": "2026-05-20"}
)
```

| Test | Scripted scenario | Fact assertions |
|------|-------------------|-----------------|
| `test_full_breach_report` | 3 rejected orders: 2 INSUFFICIENT_FUNDS + 1 WASH_SALE | `"3"` in answer; both violation type names present; repeat offender account mentioned |
| `test_no_breaches_returns_clear_message` | `list_orders` returns empty content | Answer says no breaches; `get_compliance_result` never called |
| `test_wash_sale_filter` | 3 orders: 1 WASH_SALE + 2 INSUFFICIENT_FUNDS; user asks wash-sale only | `"WASH_SALE"` in answer; agent does not lead with INSUFFICIENT_FUNDS detail |
| `test_repeat_offender_flagged` | Same `userId` appears in 2 of 3 results | Answer references that account; `"repeat"` or `"2"` associated with that account |
| `test_get_order_never_called` | Full run | `get_order` never invoked — confirm removed tool stays removed |
| `test_pagination_followed` | First page `last=false` with 25 results; second page has 3 | Total breach count reflects 28; `list_orders` called twice with page params |

---

---

**Tier 3 — Golden File Replay (manual, not in CI)**

**`tests/record_golden.py`** — one-time recorder. Hits the real Anthropic API and saves the full conversation (every model response, tool call, tool result, and final answer) to `tests/fixtures/golden_compliance_today.json`. Run once after a major prompt change or model upgrade.

**`tests/test_compliance_agent_e2e.py`** — replay harness. Mocks `anthropic.Anthropic` to return saved responses instead of calling the network. Tool handlers still call real services (Docker stack required). Same fact assertions as Tier 2.

```bash
# Step 1 — record (requires Docker stack + ANTHROPIC_API_KEY)
python equiflow-mcp/tests/record_golden.py "Show me today's compliance breaches"

# Step 2 — replay (no API call — deterministic)
pytest equiflow-mcp/tests/test_compliance_agent_e2e.py -v
```

**Golden file structure:**
```json
{
  "question": "Show me today's compliance breaches",
  "turns": [
    {
      "model_response": { "stop_reason": "tool_use", "content": [...] },
      "tool_results": [{ "name": "list_orders", "result": "..." }]
    },
    {
      "model_response": { "stop_reason": "end_turn", "content": [{ "text": "..." }] }
    }
  ]
}
```

**When to re-record:** system prompt changed, model version upgraded, new tool added to compliance agent, seed data changed significantly.

**What Tier 3 catches that Tier 2 misses:** model upgraded and now calls tools in a different order than the scripted mock expected; new tool added to the server confuses the model into calling it unnecessarily; real API response shape changed and the mocked tool return in Tier 2 was stale.

---

**File structure:**

```
equiflow-mcp/
  tests/
    __init__.py
    fixtures/
      list_orders_3_rejected.json
      compliance_result_insufficient_funds.json
      compliance_result_wash_sale.json
      golden_compliance_today.json        # Tier 3 golden fixture (committed after recording)
    test_loop.py
    test_handlers.py
    test_compliance_agent_behavior.py
    record_golden.py                      # Tier 3 recorder (run manually)
    test_compliance_agent_e2e.py          # Tier 3 replay harness
  requirements-test.txt              # pytest, pytest-asyncio, pytest-mock
```

**Run all tests:**
```bash
cd equiflow-mcp
pip install -r requirements-test.txt
pytest tests/ -v
```

**Acceptance Criteria:**
- [ ] All Tier 1 tests pass with zero real network calls (verified by asserting no `httpx` or `anthropic` calls reach external hosts)
- [ ] All Tier 2 behavioral tests pass with zero real Anthropic or HTTP calls
- [ ] `pytest tests/` exits 0 in CI
- [ ] Each test has a one-line docstring stating the scenario it covers
- [ ] `record_golden.py` exists and produces a valid golden fixture when run against a live stack
- [ ] `test_compliance_agent_e2e.py` replays the golden fixture with zero Anthropic API calls and passes fact assertions

---

---

### EQ-134 · Agent Test Suite — Settlement Reconciliation Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Engineering | 3 | P1 — depends on EQ-131 |

**Problem:** Same two failure modes as EQ-133, applied to `settlement_agent.py`. The settlement agent has more branching than the compliance agent (SETTLED vs PENDING_SETTLEMENT paths, retry classification, ledger balance checks) — behavioral tests are especially important here because a wrong branch produces a misleading ops recommendation.

**Tier 1 — Unit Tests**

**`tests/test_handlers.py` additions** — new handlers introduced in EQ-131.

| Test | Scenario | Assert |
|------|----------|--------|
| `test_list_settlements_success` | 200 response | Calls `GET /settlement/records?orderId=...`; returns response text |
| `test_list_settlements_not_found` | 404 response | Returns `"Error 404: ..."` text; no exception |
| `test_get_ledger_account_success` | 200 response | Calls `GET /ledger/accounts/{userId}`; returns response text |
| `test_get_ledger_account_not_found` | 404 response | Returns `"Error 404: ..."` text; no exception |

**Tier 2 — Behavioral Tests**

**`tests/test_settlement_agent_behavior.py`**

| Test | Scripted scenario | Fact assertions |
|------|-------------------|-----------------|
| `test_all_settled_clean_report` | 5 FILLED orders, all `status=SETTLED` | Answer says all settled; no stuck orders mentioned; `get_ledger_account` never called |
| `test_one_stuck_order_flagged` | 5 FILLED orders, 1 `PENDING_SETTLEMENT`; audit log shows 1 retry; balance sufficient | Answer mentions 1 stuck order; retry count present; recommended action present |
| `test_insufficient_balance_escalate` | 1 stuck order; balance below fill amount | Answer recommends manual credit or escalation; account balance mentioned |
| `test_no_filled_orders_today` | `list_orders` returns empty | Answer says no filled orders; no further tool calls |
| `test_retry_count_drives_recommendation` | 1 stuck order; audit log shows 3 retries | Answer does not recommend auto-retry (threshold exceeded); escalation mentioned |
| `test_multiple_stuck_orders_all_reported` | 3 stuck orders across 2 users | All 3 referenced in answer; per-order detail present |

**Run:**
```bash
pytest tests/test_handlers.py tests/test_settlement_agent_behavior.py -v
```

**Acceptance Criteria:**
- [ ] All Tier 1 handler tests for new EQ-131 endpoints pass with zero real HTTP calls
- [ ] All Tier 2 behavioral tests pass with zero real Anthropic or HTTP calls
- [ ] `pytest tests/` exits 0 in CI alongside EQ-133 tests

---

### EQ-135 · Agent Test Suite — Order Failure Escalation Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Engineering | 3 | P1 — depends on EQ-132 |

**Problem:** The escalation agent has explicit decision rules in its system prompt — specific failure reasons map to specific actions (retry, manual credit, incident). A behavioral test suite is the only way to verify the agent follows those rules correctly with a given set of tool results. A wrong branch here creates or suppresses a PagerDuty incident.

**Tier 1 — Unit Tests**

**`tests/test_handlers.py` additions** — new handler introduced in EQ-132.

| Test | Scenario | Assert |
|------|----------|--------|
| `test_create_incident_returns_mock_id` | Handler called with order_id + reason | Returns JSON with `incident_id` matching `PD-XXXXXXXX` pattern |
| `test_create_incident_includes_order_id` | Any call | `order_id` present in returned payload |
| `test_get_ledger_account_builds_correct_url` | Handler called with `user_id` | Calls `GET /ledger/accounts/{user_id}` with auth header |
| `test_get_ledger_account_http_error` | Gateway returns 404 | Returns `"Error 404: ..."` text; no exception |

**Tier 2 — Behavioral Tests**

**`tests/test_escalation_agent_behavior.py`**

Each test scripts the full tool chain: `get_order` → `get_saga` → optional further calls.

| Test | Scripted scenario | Fact assertions |
|------|-------------------|-----------------|
| `test_timeout_low_retries_recommends_retry` | `failure_reason=TIMEOUT`, `retry_count=1` | Answer recommends retry; `create_incident` never called |
| `test_network_error_at_retry_limit_escalates` | `failure_reason=NETWORK_ERROR`, `retry_count=3` | `create_incident` called; answer mentions retry limit reached |
| `test_compliance_rejection_no_action` | `failure_reason=COMPLIANCE_FAILED` | Answer says expected rejection; no incident; no ledger lookup |
| `test_insufficient_funds_balance_now_sufficient` | `failure_reason=INSUFFICIENT_FUNDS`; ledger shows balance > fill amount | Answer recommends manual retry; `create_incident` not called |
| `test_insufficient_funds_balance_still_low` | `failure_reason=INSUFFICIENT_FUNDS`; ledger shows balance < fill amount | `create_incident` called; answer mentions account balance |
| `test_compensating_saga_always_escalates` | `saga.status=COMPENSATING` | `create_incident` always called regardless of failure reason; answer mentions reconciliation |
| `test_root_cause_always_stated` | Any scenario | `failure_reason` value from data appears in answer |

**Run:**
```bash
pytest tests/test_handlers.py tests/test_escalation_agent_behavior.py -v
```

**Acceptance Criteria:**
- [ ] All Tier 1 handler tests for `create_incident` pass with zero real HTTP calls
- [ ] All Tier 2 behavioral tests pass with zero real Anthropic or HTTP calls
- [ ] Decision rules in the system prompt are each covered by at least one test case
- [ ] `pytest tests/` exits 0 in CI alongside EQ-133 and EQ-134 tests

---

### EQ-136 · Duplicate Order Detection Agent
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Feature | 5 | P1 |

**Purpose:** Detects when the same user submits identical orders within a short window and classifies each pair as HIGH, MEDIUM, or LOW suspicion based on the time gap.

**Invocation pattern:** On-demand — a compliance officer runs it to scan for suspicious duplicate trades, or after running the seed script to verify the scenario works.

**Problem it solves:** When a user submits the same order twice — different UUID but identical fields — both orders process successfully. Nothing fails. No alert fires. The most common real-world causes are a double-click on a mobile submit button, a client SDK retry with a new UUID after a network timeout, or a bot with no deduplication logic. Today a compliance officer has no automated tool to surface this. This agent scans all orders in a given time window, identifies pairs where all business fields match except the UUID, and assigns a suspicion level based on time proximity.

**Duplicate definition:** two or more orders sharing the same `userId + ticker + side + quantity + limitPrice + type` but different UUIDs.

**Suspicion levels:**

| Time between orders | Level | Interpretation |
|---|---|---|
| < 5 seconds | HIGH | Almost certainly accidental — double-click or client retry |
| 5–30 seconds | MEDIUM | Possible retry or API glitch |
| > 30 seconds | LOW | May be intentional; flag for human review |

**Open questions resolved:**
- **Does `/orders/internal/all` support `userId` filtering?** No. `OrderController.listAllOrders()` hardcodes `null` as the `userId` arg (line 139). Adding `userId` as an optional query param is required.
- **How to inject the scenario?** A seed script that calls `POST /orders` twice with identical fields for a test user. No changes to surge simulator — this is a user behavior scenario, not a technical failure.

**Services affected:**

| Service | Change |
|---------|--------|
| `order-service` | Add optional `userId` query param to `GET /orders/internal/all`; pass through to `listOrders()` instead of hardcoded `null` |

**MCP change:**
`equiflow_data_server.py` — add `userId` (string, optional) to the `list_orders` tool input schema and include it in the query string builder in `handle_list_orders`.

**Tools required:**

| Tool | Status | Notes |
|------|--------|-------|
| `list_orders` | Updated | Add `userId` filter; needed to pull all orders for a user or all users in a window |

**Agent reasoning chain:**
```
list_orders(from=<start>, to=<end>, size=100)
  → group orders by (userId, ticker, side, quantity, limitPrice, type)
  → for each group with count > 1:
      calculate time delta between createdAt timestamps
      assign suspicion level: HIGH (<5s), MEDIUM (5–30s), LOW (>30s)
  → report findings with overall risk assessment
```

**System prompt:**
```
You are an EquiFlow duplicate order detection agent. Today's date is {today}.

Your goal: identify orders that appear to be duplicates — same user, ticker, side,
quantity, price, and type — placed within a short time window.

A duplicate is two or more orders with identical fields (userId, ticker, side,
quantity, limitPrice, type) but different order IDs.

Suspicion levels:
- HIGH:   < 5 seconds apart   — almost certainly accidental (double-click, client retry)
- MEDIUM: 5–30 seconds apart  — possible retry or API glitch
- LOW:    > 30 seconds apart  — may be intentional; flag for human review

Steps:
1. Call list_orders with the date range from the question (default to today).
   Use size=100 and paginate if needed to cover the full window.
2. Group orders by (userId, ticker, side, quantity, limitPrice, type).
3. For each group with more than one order, calculate time between them and assign
   a suspicion level.

Your final response must include:
- Total duplicate pairs found (0 = clean)
- A duplicate pairs table: User | Ticker | Side | Qty | Price | Gap | Suspicion | Original UUID | Duplicate UUID
- Which users appear in more than one duplicate group (repeat offenders)
- Overall assessment: CLEAR (no duplicates), REVIEW (MEDIUM/LOW only), or ESCALATE (any HIGH)

Always include UUIDs — required for cross-referencing with the seed script output.
Do not speculate on intent beyond what the time delta and fields suggest.
```

**Scenario seed script:**
`equiflow-mcp/seed_duplicate_orders.py` — sends X orders over Y milliseconds across 2 trader accounts, where Z% of the X messages are duplicates. Duplicate mechanism: every `100/Z`th message is a re-submission of the immediately preceding order for that user (same fields, new UUID assigned by the server). Duplicates are sent `--duplicate-delay` after their original rather than on the base interval.

**How orders are placed:** the script authenticates as `trader1` and `trader2` using `POST /auth/token`, then calls `POST /orders` for each message. A duplicate is placed by re-sending the exact same JSON body the user last submitted — no special flag, no idempotency key. The server assigns a new UUID to each call and processes both orders independently through the full saga. The system does not detect the duplicate at write time; the agent detects it after the fact by grouping on business fields.

> **Market hours:** requires `market.hours.bypass=true` on `order-service`. Set `MARKET_HOURS_BYPASS=true` in `.env` before running outside NYSE hours (9:30 AM–4:00 PM ET, weekdays).

User distribution (deterministic interleave, `random.seed=42`):

| User | Share of X | Notes |
|---|---|---|
| trader1 | 60% | 60 messages in default run |
| trader2 | 40% | 40 messages in default run |

**Scenario parameters (defaults):**

| Parameter | Formula | Value |
|---|---|---|
| Total messages (X) | — | 100 |
| Seed window (Y) | — | 30,000 ms |
| Duplicate % (Z) | — | 10% |
| Total duplicates | X × Z/100 | 10 |
| Total unique | X − dups | 90 |
| Duplicate step | 100 / Z | every 10th message |
| Base interval | Y / n_unique | ~333 ms |
| Duplicate delay | `--duplicate-delay` | 1 s |
| **Total seed time** | 9×333ms + 1s × 10 | **~13 s** |

> **Prices** are randomized 0.01–1000.00 per unique order. A duplicate re-sends the exact same price, which is the fingerprint the agent matches on.

**Message feed (representative sample, positions 1–25):**

| # | Type | T+ | User | Ticker | Side | Qty | Price | Note |
|---|------|----|------|--------|------|-----|-------|------|
| 1 | unique | 0.3s | trader1 | AAPL | BUY | 5 | 95.00 | — |
| 2 | unique | 0.7s | trader2 | MSFT | BUY | 5 | 95.00 | — |
| 3 | unique | 1.0s | trader1 | TSLA | BUY | 5 | 95.00 | — |
| 4 | unique | 1.3s | trader2 | AMZN | BUY | 5 | 95.00 | — |
| 5 | unique | 1.7s | trader1 | GOOGL | BUY | 5 | 95.00 | — |
| 6 | unique | 2.0s | trader1 | META | BUY | 5 | 95.00 | — |
| 7 | unique | 2.3s | trader2 | NVDA | BUY | 5 | 95.00 | — |
| 8 | unique | 2.7s | **trader1** | **NFLX** | **BUY** | **5** | **95.00** | original |
| 9 | unique | 3.0s | trader2 | AMD | BUY | 5 | 95.00 | — |
| **10** | **DUPLICATE** | **4.0s** | **trader1** | **NFLX** | **BUY** | **5** | **95.00** | copies #8, +1s |
| 11 | unique | 8.3s | trader1 | INTC | BUY | 5 | 95.00 | — |
| 12 | unique | 8.7s | trader2 | AAPL | SELL | 5 | 95.00 | — |
| 13 | unique | 9.0s | trader1 | MSFT | SELL | 5 | 95.00 | — |
| 14 | unique | 9.3s | trader2 | TSLA | SELL | 5 | 95.00 | — |
| 15 | unique | 9.7s | trader1 | AMZN | SELL | 5 | 95.00 | — |
| 16 | unique | 10.0s | trader2 | GOOGL | SELL | 5 | 95.00 | — |
| 17 | unique | 10.3s | trader1 | META | SELL | 5 | 95.00 | — |
| 18 | unique | 10.7s | **trader2** | **NVDA** | **SELL** | **5** | **95.00** | original |
| 19 | unique | 11.0s | trader1 | NFLX | SELL | 5 | 95.00 | — |
| **20** | **DUPLICATE** | **8.0s** | **trader2** | **NVDA** | **SELL** | **5** | **95.00** | copies #18, +1s |
| 21 | unique | 16.3s | trader2 | AMD | SELL | 5 | 95.00 | — |
| 22 | unique | 16.7s | trader1 | INTC | SELL | 5 | 95.00 | — |
| ... | ... | ... | ... | ... | ... | ... | ... | pattern repeats |

**Duplicate pairs (end of run, default scenario):**

Prices are random per run — table shows structure only.

| Dup # | Orig # | User | Ticker | Side | Qty | Price | Gap | Suspicion |
|---|---|---|---|---|---|---|---|---|
| 10 | 8 | trader1 | NFLX | BUY | 5 | rand | ~1s | **HIGH** |
| 20 | 18 | trader2 | NVDA | SELL | 5 | rand | ~1s | **HIGH** |
| 30 | 28 | trader1 | AAPL | SELL | 10 | rand | ~1s | **HIGH** |
| 40 | 38 | trader2 | META | BUY | 10 | rand | ~1s | **HIGH** |
| 50 | 48 | trader1 | TSLA | BUY | 15 | rand | ~1s | **HIGH** |
| 60 | 58 | trader2 | AMZN | SELL | 15 | rand | ~1s | **HIGH** |
| 70 | 68 | trader1 | NVDA | BUY | 20 | rand | ~1s | **HIGH** |
| 80 | 78 | trader2 | INTC | SELL | 20 | rand | ~1s | **HIGH** |
| 90 | 88 | trader1 | AMD | BUY | 25 | rand | ~1s | **HIGH** |
| 100 | 98 | trader2 | GOOGL | SELL | 25 | rand | ~1s | **HIGH** |

Up to 10 pairs — all HIGH → agent outputs **ESCALATE**.

**Scenario variants (UI presets → randomised delay ranges):**

| UI button | `--duplicate-delay` | `--max-delay` | Gap range (random per pair) | Suspicion | Expected assessment |
|---|---|---|---|---|---|
| HIGH | `1s` | `4s` | 1s–4s | HIGH | ESCALATE |
| MED | `10s` | `25s` | 10s–25s | MEDIUM | REVIEW |
| LOW | `60s` | `120s` | 60s–120s | LOW | REVIEW |

Each duplicate pair gets an independently drawn `random.uniform(min, max)` gap — the seed does not use a fixed delay.

Unique orders sent = `X * (1 - Z/100)`. Total messages sent = X.
Seed script writes `scenario_pairs.json` on completion; agent writes `agent_findings.json`. Both are gitignored runtime artifacts.

**Seed script interface:**
```bash
python equiflow-mcp/seed_duplicate_orders.py \
  --messages 100 \       # X: total orders to send (duplicates count toward this total)
  --duration 30000 \     # Y: total window in ms for unique orders
  --duplicate-pct 10 \   # Z: % of X that are duplicates (default 10)
  --duplicate-delay 1s   # delay between original and its duplicate (default 1s → HIGH suspicion)
                         # use 10s for MEDIUM, 60s for LOW
```

**Example usage:**
```bash
# 0. Clean previous test data
python equiflow-mcp/cleanup_scenario.py --execute

# 1. Seed — default 100 msgs, 10% dups, 1s delay → ESCALATE; writes scenario_pairs.json
python equiflow-mcp/seed_duplicate_orders.py

# Quick run (10 msgs, 1 dup, ~6s total)
python equiflow-mcp/seed_duplicate_orders.py --messages 10 --duration 5000 --duplicate-pct 10

# 2. Run the agent — writes agent_findings.json
python equiflow-mcp/duplicate_agent.py "Scan today's orders for duplicates"

# 3. Compare what was seeded vs what the agent found
python equiflow-mcp/compare_duplicates.py
```

**Files:**
- New: `equiflow-mcp/duplicate_agent.py`
- New: `equiflow-mcp/seed_duplicate_orders.py`
- New: `equiflow-mcp/cleanup_scenario.py`
- New: `equiflow-mcp/compare_duplicates.py`
- Modified: `equiflow-mcp/loop.py` — auto-loads `.env` API key; bumped `max_tokens` to 16 000
- Modified: `equiflow-mcp/equiflow_data_server.py` — added `userId` filter to `list_orders`
- Modified: `order-service/.../OrderController.java` — added optional `userId` query param to `GET /orders/internal/all`
- Modified: `order-service/.../MatchingEngine.java` — fixed divide-by-zero when `totalFilled = 0`
- Modified: `docker-compose.yml` — added Kafdrop at port 9000
- Modified: `.env.example` — added `MARKET_HOURS_BYPASS=true`

**Acceptance Criteria:**
- [x] `GET /orders/internal/all?userId=<uuid>` returns only orders for that user
- [x] `GET /orders/internal/all` with no `userId` still returns all orders (backwards compatible)
- [x] `list_orders` MCP tool accepts and passes through `userId` param
- [x] `seed_duplicate_orders.py` places duplicate orders and writes `scenario_pairs.json` with orig/dup UUIDs
- [x] Running the agent after the seed detects the pairs, outputs UUID table, and writes `agent_findings.json`
- [x] `compare_duplicates.py` reads both JSON files and shows ✓ FOUND / ✗ MISSED / ! EXTRA per pair with detection rate
- [x] Agent correctly reports CLEAR when no duplicates exist in the window
- [x] Agent paginates if total orders exceed the page size

---

### EQ-137 · Agent Visualization Frontend
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| AI Agents | Feature | 5 | P1 |

**Purpose:** Streams each agent's tool calls and reasoning steps live into a React UI so runs are observable, debuggable, and demoable without a terminal.

**Problem it solves:**
The three Claude API agents (compliance, duplicate detection, order triage) run exclusively from the CLI. There is no way to observe agent reasoning in real time — iteration steps, tool calls, tool results, and the final answer are only visible in terminal output. This makes the agents difficult to demo, hard to debug, and invisible to non-engineers.

**What was built:**
A full-stack agent visualization interface: a FastAPI SSE streaming backend that wraps all three existing agents, and a React + TypeScript + Tailwind frontend that renders each agent step as a live card timeline as events arrive.

**Architecture:**

```
React (Vite) → fetch + ReadableStream → FastAPI SSE → streaming_loop.py → Claude API
                                                     → call_tool → EquiFlow services
```

**New files:**

| File | Purpose |
|------|---------|
| `equiflow-mcp/streaming_loop.py` | Async generator variant of `run_agent()` — yields typed events, emits `token_usage` per iteration, uses `client.messages.stream()` context manager for large `max_tokens` |
| `equiflow-mcp/api.py` | FastAPI app with `POST /api/run` (mode: live/local), `POST /api/seed`, `POST /api/cleanup`, and `GET /api/agents` endpoints; routes LOCAL to per-agent planner |
| `equiflow-mcp/local_loop.py` | Thin wrapper driving per-agent planners with the same interface as `streaming_loop.py` |
| `equiflow-mcp/playbooks/` | Per-agent rule-based planners (`duplicate.py`, `compliance.py`, `triage.py`) + shared `base.py` helpers |
| `equiflow-mcp/MODES.md` | Reference: LIVE vs LOCAL — what runs, cost, per-agent support, planner architecture, event schema |
| `frontend/` | Vite + React + TypeScript + Tailwind project |
| `frontend/src/types.ts` | `AgentEvent` union (iteration_start, tool_call, tool_result, token_usage, done, error) + `SeedEvent` |
| `frontend/src/components/AgentRunner.tsx` | Two-segment `[LIVE][LOCAL]` mode toggle; agent picker; question input; run/stop/clear; seed log panel; status bar with live token counts |
| `frontend/src/components/Timeline.tsx` | Left-rail iteration timeline; `ResultPanel` renders structured findings table (verdict banner, duplicate pairs, repeat offenders) from `findings_json` object |

**Modified files:**

| File | Change |
|------|--------|
| `equiflow-mcp/duplicate_agent.py` | Slimmed system prompt (~450 tokens removed); `findings_json` changed from array to object `{verdict, total_orders, pairs}` |
| `equiflow-mcp/streaming_loop.py` | Switched to `AsyncAnthropic` + `client.messages.stream()` to support `max_tokens=32000`; emits `token_usage` events |
| `equiflow-mcp/seed_duplicate_orders.py` | All Unicode → ASCII; ASCII progress bar; `--max-delay` arg added |
| `equiflow-mcp/cleanup_scenario.py` | Fixed table name `compliance_results` → `compliance_checks`; removed stale `account_holds` references; all Unicode → ASCII |
| `equiflow-mcp/requirements.txt` | Added `fastapi`, `uvicorn[standard]`, `sse-starlette` |
| `frontend/vite.config.ts` | Added Tailwind plugin + `/api` proxy to `http://localhost:8000` |
| `frontend/src/index.css` | Background `#f0f3f5`, tabular numerals, `-0.015em` letter-spacing |

**Run modes (two-way toggle):**

| Mode | What runs | Cost | Supported agents |
|------|-----------|------|-----------------|
| **LIVE** | Real DB + real Anthropic API | Tokens per run | All |
| **LOCAL** | Real DB + rule-based planner; no Anthropic | Free | All (via `playbooks/`) |

See `equiflow-mcp/MODES.md` for full per-agent support matrix and planner architecture.

**Agent event types (`AgentEvent`):**

| Event | Fields | Description |
|-------|--------|-------------|
| `iteration_start` | `iteration` | New loop iteration — numbered circle on the rail |
| `tool_call` | `name`, `input` | Tool name + collapsible JSON input |
| `tool_result` | `name`, `result` | Tool name + collapsible JSON result |
| `token_usage` | `iteration`, `input_tokens`, `output_tokens` | Per-iteration token counts (LIVE only) |
| `done` | `answer` | Final answer — rendered as structured findings table or narrative |
| `error` | `message` | Error message below `✕` terminal node |

**Token optimizations applied:**

- System prompt slimmed from ~650 to ~200 tokens (removed table format, redundant narrative)
- Tool schema: removed unused filter fields (`userId`, `ticker`, `status`)
- Tool result slimming (`_list_orders_slim`): 8 essential fields only, truncated `createdAt[:19]`, compact JSON separators, stripped pagination metadata
- `findings_json` changed from array to object so verdict and order count don't require narrative regex parsing on the frontend

**How to run:**

```bash
# Terminal 1 — FastAPI backend
cd equiflow-mcp
uvicorn api:app --reload --port 8000

# Terminal 2 — React frontend
cd frontend
npm run dev
# Open http://localhost:5173
```

**Seed section (global — top of sidebar):**

A "Test Data" panel sits above the agent list in the sidebar and is not tied to any single agent card. It shows three buttons (HIGH / MED / LOW) that always seed the Duplicate Detection scenario. Clicking any button also navigates to the Duplicate Detection agent.

**Seed button flow:**
1. Click HIGH / MED / LOW — calls `POST /api/seed { agent: "duplicate", level: "HIGH"|"MED"|"LOW" }`
2. Backend maps level to a delay range: HIGH→1s–4s, MED→10s–25s, LOW→60s–120s
3. Backend runs `cleanup_scenario.py --execute` (removes all non-Flyway orders across 5 DBs, ~2s)
4. Backend runs `seed_duplicate_orders.py --messages 20 --duration 5000 --duplicate-delay <min> --max-delay <max>`
5. Each duplicate pair sleeps `random.uniform(min, max)` seconds — gap varies per pair within the range
6. Stdout from both scripts streams as SSE log lines to the UI log panel
7. Log panel shows phase headers (── Cleanup / ── Seed) with live output beneath
8. On completion: "✓ Seed complete" — run the agent to see today's duplicate orders

**Design decisions:**
- `streaming_loop.py` is a separate file — `loop.py` is untouched so all existing CLI agents continue to work unchanged
- The FastAPI DISPATCH table is hard-coded per agent — each agent exposes only the tools it actually uses
- SSE via `ReadableStream` + manual line parsing (no `EventSource`) so the client can use `POST` with a JSON body
- `POST /api/seed` streams script stdout line-by-line as SSE — no blocking
- `client.messages.stream()` context manager required by Anthropic SDK when `max_tokens > 10,000` — switched from `messages.create()` to satisfy this constraint
- TWO-WAY MODE TOGGLE: LIVE (red `#c0392b`), LOCAL (dark teal `#19535f`) — color encodes whether Anthropic API is called; toggle sits above the input row, input fills full width with RUN beside it
- LOCAL mode routes to a per-agent rule-based planner in `playbooks/` — real DB calls, same SSE event stream, no LLM; planners implement the happy-path decision rules from each system prompt
- `findings_json` is now a structured object (not array) — `ResultPanel` parses verdict + pairs directly without regex scraping the narrative
- Modern finance UI: `#f0f3f5` background, teal accent (`#0b7a75`/`#19535f`), amber for warning states, zinc palette, monospace badges

**Acceptance Criteria:**
- [x] `POST /api/run` streams iteration, tool call, tool result, and done events for all three agents
- [x] Sidebar shows all 5 agents with LIVE/SOON badges; SOON agents are disabled
- [x] Timeline renders left-rail layout with numbered circles, connector lines, and collapsible JSON
- [x] Run button starts streaming; Stop button cancels; Clear resets timeline
- [x] Status bar shows green pulse while running, tool call count when done, error on failure
- [x] Seed section is a global control at the top of the sidebar (not per-agent); HIGH / MED / LOW buttons always seed the duplicate scenario and navigate to the Duplicate Detection agent
- [x] SEED streams cleanup + seed log output in real time; log panel shows phase headers and raw lines
- [x] Log panel auto-scrolls; shows ✓ / ✕ header and dismiss button on completion
- [x] `streaming_loop.py` does not modify `loop.py`
- [x] `api.py` imports all three agent modules without error
- [x] Two-way `[LIVE][LOCAL]` mode toggle; mode sent in request body
- [x] LOCAL mode runs rule-based planner against real DB — no Anthropic credits consumed; all three agents supported
- [x] Token usage (`N in / N out tokens`) displayed in status bar for LIVE runs
- [x] `findings_json` structured object parsed into verdict banner + findings table + repeat offenders panel

---

---

## EQ-139 · Extend LOCAL Mode to All Agents via Rule-Based Planners

| Epic | Type | Branch |
|------|------|--------|
| AI Agent Monitoring | Enhancement | `EQ-139-local-mode-complex-agents` |

**Purpose:** Replace single-agent LOCAL mode (duplicate only) with a planner architecture that covers all agents, removing MOCK entirely.

**What changed:**
- Removed MOCK mode from frontend toggle, `api.py`, and all docs — fixtures go stale and added maintenance overhead with no real benefit over LOCAL
- Introduced `playbooks/` directory: one `run(question, call_tool)` function per agent that encodes the system prompt's decision rules in Python, calls real tool handlers, and emits the identical SSE event stream
- Added `local_loop.py` — thin wrapper with the same interface as `streaming_loop.py`
- `api.py` routes LOCAL requests to `PLAYBOOKS[agent].run` via `run_agent_local`; `call_tool` is shared between LOCAL and LIVE so each planner uses the correct dispatch (e.g. `_list_orders_slim` for duplicate)
- Fixed `compliance.py` planner: API returns `code` not `type` in violation objects, and `WASH_SALE` not `WASH_SALE_VIOLATION` — planner now normalises both
- Fixed `triage.py` planner: surfaces actual API error message when `get_order` returns a non-200 response instead of generic "Failed to parse order response"
- UI: LIVE button colour changed to red (`#c0392b`); toggle moved above input row; input fills full container width with RUN beside it

**Planner decision rules:**

| Planner | Key logic |
|---------|-----------|
| `duplicate.py` | Group by `(userId, ticker, side, qty, price, type)` → sort → compute gap → HIGH/MED/LOW |
| `compliance.py` | List REJECTED → fetch each compliance result → normalise violation code → group by userId → flag repeat offenders |
| `triage.py` | Extract UUID from question (or fall back to most recent FAILED) → get_order → get_saga → query_audit_log → match COMPENSATING/TIMEOUT/COMPLIANCE/INSUFFICIENT_FUNDS rules |

**Files created:** `local_loop.py`, `playbooks/__init__.py`, `playbooks/base.py`, `playbooks/duplicate.py`, `playbooks/compliance.py`, `playbooks/triage.py`

**Files modified:** `api.py`, `AgentRunner.tsx`, `MODES.md`, `SPEC.md`, `CONTRIBUTION_PLAN.md`

**Files removed:** `fixtures/duplicate.jsonl`

**Acceptance Criteria:**
- [x] LOCAL mode runs all three agents against real DB — no Anthropic credits consumed
- [x] Same SSE event types emitted as LIVE (no `token_usage`)
- [x] `· local` badge shown in status bar
- [x] MOCK toggle removed from UI and backend
- [x] `MODES.md` updated to two-mode architecture with planner docs
- [x] Compliance planner correctly normalises `WASH_SALE` → `WASH_SALE_VIOLATION` and reads `code` field
- [x] Triage planner surfaces actual API error when order is not found
- [x] LIVE button is red; toggle is above input row; input fills full width

---

## EQ-140 · Elevate BOT_OPERATOR Read Permissions Across Agent-Facing Endpoints

| Epic | Type | Points |
|------|------|--------|
| AI Agent Monitoring | Security / Enhancement | 3 |

**Purpose:** Allow bot-operator agents to fetch any order by UUID without an ownership filter, unblocking the triage agent and any future agent that needs cross-user read access.

**Problem:**
`GET /orders/{orderId}` calls `orderRepository.findByIdAndUserId(orderId, userId)` — it filters by both the order ID and the authenticated user's userId. Bot-operators authenticate as a service account (`bot-operator1`, userId `a1000000-0000-0000-0000-000000000003`) but the orders they need to triage belong to trader accounts. The endpoint returns `400 Order not found` even when the order exists.

`GET /orders/internal/all` already bypasses ownership filtering for bot-operators (it passes `null` userId to `listOrders`). `GET /orders/{id}` should be consistent.

**Root cause in `OrderService.java`:**
```java
public OrderResponse getOrder(UUID orderId, UUID userId) {
    Order order = orderRepository.findByIdAndUserId(orderId, userId)  // ← filters by owner
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    return toResponse(order);
}
```

**Implementation:**

1. **`OrderController.java`** — check the caller's role before dispatching:
```java
@GetMapping("/{orderId}")
public ResponseEntity<OrderResponse> getOrder(@PathVariable UUID orderId, Authentication auth) {
    boolean isAgent = auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_BOT_OPERATOR"));
    if (isAgent) {
        return ResponseEntity.ok(orderService.getOrderInternal(orderId));
    }
    return ResponseEntity.ok(orderService.getOrder(orderId, extractUserId(auth)));
}
```

2. **`OrderService.java`** — add `getOrderInternal` (no ownership filter):
```java
public OrderResponse getOrderInternal(UUID orderId) {
    Order order = orderRepository.findById(orderId)   // ← already used by triggerMatch
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
    return toResponse(order);
}
```

3. **`equiflow_data_server.py`** — no change needed; `handle_get_order` already calls `GET /orders/{id}` and the bot-operator token is used automatically.

**Scope:** Read-only change. No write paths, no new endpoints, no schema changes. The bot-operator role already exists in JWT claims — this just adds a branch on it in one controller method.

**Files to modify:**
| File | Change |
|------|--------|
| `order-service/.../OrderController.java` | Add role check in `getOrder`, dispatch to `getOrderInternal` for BOT_OPERATOR |
| `order-service/.../OrderService.java` | Add `getOrderInternal(UUID orderId)` using `findById` |

**Acceptance Criteria:**
- [ ] `GET /orders/{id}` returns the order for a BOT_OPERATOR token regardless of who owns the order
- [ ] `GET /orders/{id}` still enforces ownership for TRADER tokens (no regression)
- [ ] Triage agent LOCAL mode successfully fetches a FAILED order by UUID and proceeds to get_saga + audit_log steps
- [ ] No changes to write endpoints (POST, DELETE, system-cancel)

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

