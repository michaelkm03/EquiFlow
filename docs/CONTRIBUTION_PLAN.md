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
| ⚪ | <nobr>[EQ-113b](#eq-113b--target-service-idempotency)</nobr> | Target Service Idempotency — system-cancel endpoint on order-service; release() idempotency on ledger-service | 2 | P0 — can start parallel with EQ-113a |
| ⚪ | <nobr>[EQ-113c](#eq-113c--saga-compensation-wiring--recovery-job)</nobr> | Saga Compensation Wiring + Recovery — cancel/release steps in failSaga(); SagaStep recording; SagaRecoveryJob | 3 | P0 — depends on EQ-113a, EQ-113b |
| ⚪ | <nobr>[EQ-115](#eq-115--saga-settlement-failure--manual-reconciliation)</nobr> | Settlement Failure Handling — step 4 guard, COMPENSATION_REQUIRED status, ops Kafka alert, credit endpoint | 3 | P0 — depends on EQ-113c |
| ⚪ | <nobr>[EQ-116](#eq-116--saga-data-integrity-test-suite)</nobr> | Data Integrity Test Suite — Testcontainers end-to-end assertion of all three service DBs per compensation scenario | 3 | P0 — depends on EQ-115, EQ-113c |
| ⚪ | <nobr>[EQ-114](#eq-114--remove-redundant-synchronous-order-matching-in-submitorder)</nobr> | Remove Redundant Synchronous Matching — order matching runs twice; saga must be the sole execution path | 3 | P1 |

### Backlog — Features
| Status | Ticket | Feature | Points |
|--------|--------|---------|--------|
| ⚪ | <nobr>[EQ-201](#eq-201--trade-confirmation-documents)</nobr> | Trade Confirmation Documents — generate a receipt for every completed trade | 5 |
| ⚪ | <nobr>[EQ-202](#eq-202--account-funding--deposit-and-withdrawal)</nobr> | Account Funding — let users add or withdraw money from their account | 8 |
| ⚪ | <nobr>[EQ-203](#eq-203--wash-sale-compliance--user-facing-warning)</nobr> | Wash-Sale User Warning — when a trade is blocked by tax rules, explain why and when the user can try again | 3 |

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
- `OrderService.cancelOrder()` terminal state rules:
  - `CANCELLED` or `REJECTED` → return silently (no-op; no DB write)
  - `FILLED` → return HTTP 409 `{ "error": "ORDER_IN_TERMINAL_STATE", "status": "FILLED", "orderId": "..." }` and log WARN
  - `PENDING` or `OPEN` → cancel normally

*ledger-service:*
- Add `existsByOrderIdAndType(orderId, type)` to `LedgerTransactionRepository`
- In `LedgerService.release()`: check for an existing RELEASE transaction by orderId before modifying balances. If found, return current account state without writing a new transaction. HTTP 200 either way.

**Services Affected:** `order-service`, `ledger-service`.

**Acceptance Criteria:**
- [ ] `POST /orders/{orderId}/system-cancel` cancels a PENDING or OPEN order; returns HTTP 200; publishes `order.cancelled`
- [ ] System-cancel on a CANCELLED or REJECTED order returns HTTP 200 with no DB change
- [ ] System-cancel on a FILLED order returns HTTP 409 with `ORDER_IN_TERMINAL_STATE` body; WARN logged
- [ ] `LedgerService.release()` called twice with the same orderId: second call returns HTTP 200; `cash_on_hold` unchanged; no second RELEASE transaction written

**Test Cases (unit, Mockito):**

| File | Method | Assert |
|------|--------|--------|
| `OrderServiceTest` | `cancelOrder_alreadyCancelled_isNoOp` | Returns silently; `orderRepository.save()` not called |
| `OrderServiceTest` | `cancelOrder_rejectedOrder_isNoOp` | Returns silently; no DB write |
| `OrderServiceTest` | `cancelOrder_filledOrder_returns409` | HTTP 409 returned; WARN logged; order status unchanged |
| `LedgerServiceTest` | `release_duplicateOrderId_isNoOp` | `existsByOrderIdAndType` returns true; no balance change; no second tx written |

```bash
mvn test -pl order-service -Dtest=OrderServiceTest
mvn test -pl ledger-service -Dtest=LedgerServiceTest#release_duplicateOrderId_isNoOp
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

## Backlog — Approved, Not Yet Scheduled

---

### EQ-201 · Trade Confirmation Documents
| Epic | Type | Points |
|------|------|--------|
| Compliance & Reporting | Feature | 5 |

**Product Request:**
> "Regulatory requirement. Every executed trade needs a confirmation document
> showing the user what was traded, at what price, and what fees were applied.
> Must be available for download within 24 hours of the trade."

**Functionality:**
A new endpoint on `order-service` returns a structured trade confirmation for
any filled order. The confirmation is generated on-demand from existing order
data — no separate document store is required at this stage. Every access is
logged by `audit-service` for regulatory traceability.

**Services Affected:**

| Service | Change |
|---------|--------|
| `order-service` | New `GET /orders/{id}/confirmation` endpoint; confirmation DTO assembly from fill data |
| `audit-service` | Consumes `trade.confirmation.accessed` Kafka event |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `order-service` | No schema changes; reads from existing `orders` and `order_fills` tables |

**Kafka Topics:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `trade.confirmation.accessed` | `order-service` | `audit-service` | Regulatory log of every confirmation retrieval |

**Happy Path:**
1. User calls `GET /orders/{id}/confirmation` for a filled order
2. `order-service` assembles the confirmation from the order record and fill data
3. A `trade.confirmation.accessed` event is published to Kafka
4. Response returns the structured confirmation

**Edge Cases:**
- Order exists but is not yet filled (`PENDING`, `PENDING_TRIGGER`) → HTTP 409 with message indicating the order has not settled
- Order ID does not exist → HTTP 404
- Order belongs to a different user → HTTP 403
- Order was partially filled → confirmation reflects the filled quantity and weighted average fill price only

**Acceptance Criteria:**
- [ ] `GET /orders/{id}/confirmation` returns a structured confirmation for any `FILLED` order
- [ ] Confirmation includes: order ID, ticker, type, quantity, fill price, timestamp, estimated fee
- [ ] `AuditService` logs every confirmation access via Kafka event
- [ ] Requesting a confirmation for a non-filled order returns HTTP 409
- [ ] Requesting a confirmation for another user's order returns HTTP 403

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

---

### EQ-203 · Wash-Sale Compliance — User-Facing Warning
| Epic | Type | Points |
|------|------|--------|
| Compliance & Reporting | Feature | 3 |

**Product Request:**
> "Right now wash-sale violations are silently blocked. Users don't understand
> why their order was rejected. We need a clear explanation returned with the
> rejection so users can make an informed decision."

**Functionality:**
When `compliance-service` blocks an order due to a wash-sale violation, it
currently returns a generic rejection. This story enriches the rejection
response with a structured explanation: the rule that was violated, the date
the block expires, and the order ID of the triggering sale. No new data is
stored — this is a response enrichment on existing compliance logic.

**Services Affected:**

| Service | Change |
|---------|--------|
| `compliance-service` | Enrich the wash-sale rejection response with `complianceReason`, `blockedUntil`, and `triggerOrderId` fields |
| `saga-orchestrator` | Propagate the enriched rejection body back to the caller rather than a generic error |

**Database Changes:**

| Service DB | Change |
|------------|--------|
| `compliance-service` | No schema changes; `wash_sale_history` table already stores the triggering sale date and order ID |

**Kafka Topics:** None — compliance check is a synchronous Feign call within the saga.

**Happy Path:**
1. User submits a buy order for AAPL within 30 days of selling AAPL at a loss
2. `compliance-service` detects the wash-sale violation
3. Rejection response includes `complianceReason: "Wash-sale rule: repurchase within 30 days of a loss sale"`, `blockedUntil: "2026-04-15"`, `triggerOrderId: "ord-8821"`
4. Saga marks the order `REJECTED` and returns the enriched error to the user

**Edge Cases:**
- Multiple wash-sale violations exist for the same ticker (e.g., sold twice at a loss) → return the most recent triggering sale; `blockedUntil` is calculated from the most recent sale date
- `wash_sale_history` record is missing for the blocked order (data inconsistency) → return the rejection with `complianceReason` populated but `triggerOrderId: null`; do not fail the compliance check itself
- User re-submits the same order after `blockedUntil` has passed → order proceeds normally through compliance; no rejection

**Acceptance Criteria:**
- [ ] Wash-sale rejection response includes a `complianceReason` field with a human-readable explanation
- [ ] Response includes `blockedUntil` date (30 days after the triggering sale)
- [ ] Response includes `triggerOrderId` referencing the sale that caused the block
- [ ] If `triggerOrderId` cannot be determined, the field is returned as `null` rather than causing an error
