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
| ⚪ | <nobr>[EQ-113a](#eq-113a--compensating-status--recovery-job)</nobr> | Compensating Status + Recovery Job — crash-safe COMPENSATING checkpoint and scheduled recovery | 2 | P0 |
| ⚪ | <nobr>[EQ-113b](#eq-113b--compensation-feign-calls--idempotency-fixes)</nobr> | Compensation Feign Calls + Idempotency — cancel/release steps wired in failSaga(); both services made idempotent | 3 | P0 — depends on EQ-113a |
| ⚪ | <nobr>[EQ-115](#eq-115--saga-settlement-failure--manual-reconciliation)</nobr> | Settlement Failure Handling — step 4 guard, COMPENSATION_REQUIRED status, ops Kafka alert, credit endpoint | 3 | P0 — depends on EQ-113b |
| ⚪ | <nobr>[EQ-116a](#eq-116a--unit-idempotency-tests)</nobr> | Unit Idempotency Tests — release() and cancelOrder() idempotency cases added to existing test files | 2 | P0 — depends on EQ-113b |
| ⚪ | <nobr>[EQ-116b](#eq-116b--saga-data-integrity-test-suite)</nobr> | Data Integrity Test Suite — Testcontainers end-to-end assertion of all three service DBs per compensation scenario | 3 | P0 — depends on EQ-115, EQ-116a |
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

### EQ-113a · Compensating Status + Recovery Job
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 |

> **Scope note:** This ticket covers crash-safety infrastructure only — the COMPENSATING status checkpoint and the scheduled recovery job. No Feign calls are wired here. EQ-113b adds the actual cancel/release steps and idempotency fixes.

**What's in this ticket:**
1. `failSaga()` sets `saga.status = COMPENSATING` and saves before any Feign call — the crash-safe boundary.
2. `SagaRecoveryJob` — new `@Scheduled` class, runs every 60 seconds, queries sagas in COMPENSATING state older than 2 minutes.
3. `getSagasByStatus("COMPENSATING")` query used by the recovery job (method already exists on the service — wire it in).

**Recovery job logic:**
- For each stuck saga: read existing `SagaStep` records.
- Skip any step that has a `COMPENSATION_*` SagaStep with status=COMPLETED.
- Delegate re-compensation to the same method EQ-113b will implement — recovery job and normal failure path share the same compensation entry point.

**Services Affected:** `saga-orchestrator` only.

**Acceptance Criteria:**
- [ ] `failSaga()` writes COMPENSATING to the DB before any downstream call; verifiable by killing the pod immediately after and checking the DB row
- [ ] `SagaRecoveryJob` is registered as a `@Scheduled` bean and logs a scan entry on each run
- [ ] Recovery job skips sagas in COMPENSATING state for fewer than 2 minutes
- [ ] Recovery job skips completed compensation steps; only re-runs missing ones

**Test Cases — `SagaRecoveryJobTest` (unit, Mockito):**

| Scenario | Mock setup | Assert |
|----------|------------|--------|
| No stuck sagas | `getSagasByStatus` returns empty | No compensation called |
| Saga stuck < 2 min | Saga created 90s ago | Skipped — not yet eligible |
| Saga stuck > 2 min, no completed steps | Saga created 3 min ago; no SagaSteps | Compensation entry point called |
| Saga stuck > 2 min, cancel already completed | COMPENSATION_CANCEL SagaStep=COMPLETED present | Cancel skipped; release still called |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaRecoveryJobTest
```

---

### EQ-113b · Compensation Feign Calls + Idempotency Fixes
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-113a |

> **Scope note:** Wires the actual cancel and release compensation steps into `failSaga()`. Adds idempotency to both target services. Step 4 failure (settlement) is EQ-115.

> **Scope note (original EQ-113):** EQ-113a + EQ-113b together cover steps 1–3 compensation (cancel order, release hold) and crash-safety. Step 4 (settlement failure / debit reversal) is EQ-115. Data integrity test suite is EQ-116a + EQ-116b.

---

**Current gaps:**

| Gap | Location | Impact |
|-----|----------|--------|
| `failSaga()` does nothing except set status | `OrderSaga.java` | Orders left in PENDING/unknown state; holds never released |
| Step 2 failure has a comment `// nothing to undo yet` | `OrderSaga.java` | Order record left in PENDING after saga fails |
| Step 4 has no failure check at all | `OrderSaga.java` | Settlement failure silently falls through to `COMPLETED` — data integrity bug |
| `OrderClient` has no `cancelOrder()` | `OrderClient.java` | Cannot cancel orders from saga |
| `LedgerService.release()` is not idempotent | `LedgerService.java` | Calling twice subtracts hold twice; second call not a no-op |
| No hold is placed anywhere in the saga | `OrderSaga.java` | `release()` compensation has no hold to release — flagged as existing gap |
| `SagaService.getActiveSagas()` only returns STARTED | `SagaService.java` | COMPENSATING sagas after pod restart are never recovered |

---

**Definitions (used throughout this ticket):**

| Term | Meaning |
|------|---------|
| **Cancel Order** | Set order status → `CANCELLED` in order-service. Publishes `order.cancelled` Kafka event. No-op if already `CANCELLED` or `REJECTED`. Alerts if order is `FILLED` (that is a data integrity problem, not a duplicate). |
| **Release Hold** | Reduce `cash_on_hold` in ledger-service by the hold amount for this order. Uses `orderId` as idempotency key — checks `ledger_transactions` for existing RELEASE record first. If record exists, returns account state without modifying balances. |
| **Both** | Cancel Order, then Release Hold. Each runs independently — failure of one does not block the other. |

**When each applies:**

| Step failed | Cancel Order | Release Hold | Rationale |
|-------------|-------------|-------------|-----------|
| Step 1 — Compliance | No | No | Nothing executed. Order never reached order-service matching. |
| Step 2 — Matching | Yes | No | Order record exists in order-service in an ambiguous state. Hold may not exist (see gap above). Normalise to `CANCELLED`. |
| Step 3 — Debit | Yes | Yes | Order matched. Hold exists (if hold was placed). Debit failed so funds are still frozen in `cash_on_hold`. |

---

**Scenario 1 — Compliance fails**

Alice submits a buy order for a stock she sold at a loss 20 days ago. Compliance rejects it.

- The saga never called the matching engine. No order state change occurred beyond `PENDING`.
- No Cancel. No Release.
- `failSaga()` sets `saga.status = FAILED`, records the compliance rejection reason, and saves.
- The order in order-service stays `PENDING`. A follow-on task (outside this ticket) should transition it to `REJECTED` to make the state user-visible — flagged but not in scope here.
- **Kafka:** No compensation events. Saga failure is logged.
- **Audit:** `SagaStep` for COMPLIANCE_CHECK records status=FAILED and the rejection message. Audit-service reads this from the saga DB.

---

**Scenario 2 — Matching fails**

Bob places a limit buy. The matching engine finds no sellers and the Feign call throws.

**Why not leave the order as-is:**
The order is in `PENDING` (saga's view) or `REJECTED` (if the matching engine set it before throwing). `PENDING` is an active-waiting state — leaving it there after a saga failure misleads users and any system that polls for pending orders. `REJECTED` is already terminal but was set by the engine, not the saga; the saga should still explicitly record its compensation action for audit completeness.

- Order status after matching failure is uncertain (PENDING or REJECTED depending on where the engine failed).
- Compensation: **Cancel Order**
  - If already `CANCELLED`: silent no-op (idempotent).
  - If `REJECTED`: silent no-op — treat as already terminal, do not overwrite (order was rejected by the engine, which is a valid terminal state).
  - If `FILLED`: **do not cancel** — log `WARN compensation_anomaly orderId={} status=FILLED` and alert ops. A FILLED order during a failed saga is a data integrity issue requiring investigation, not automated cancellation.
  - If `PENDING` or `OPEN`: cancel normally.
- `failSaga()` sets `saga.status = FAILED`.
- **Kafka:** `order.cancelled` published by order-service if cancel ran. No new topics.
- **Audit:** `COMPENSATION_CANCEL` SagaStep recorded with outcome (COMPLETED or FAILED).
- **If order-service is down:** Feign call times out (configured timeout: 5s). Catch, log `ERROR compensation_cancel_failed orderId={} reason={}`, continue to `FAILED`. Hold is not applicable here. Saga reaches `FAILED`. Ops can manually cancel the order via the existing `DELETE /orders/{id}` endpoint.

---

**Scenario 3 — Debit fails**

Carol's order matched. The saga moves to debit her account. `ledger-service` is briefly down — Feign call times out.

This is the most important scenario. The order is now in a matched/filled state, and a hold (if placed) is frozen on Carol's account.

- Compensation: **Cancel Order + Release Hold** (in that order, independently)
- **Cancel Order:** same rules as Scenario 2.
- **Release Hold:**
  - Calls `ledgerClient.release({ userId, orderId, amount })`.
  - Before reducing `cash_on_hold`, `LedgerService.release()` checks `ledger_transactions` for an existing RELEASE record with this `orderId`. If found, returns current account state — no balance change. This is the primary idempotency guard.
  - If no existing record, proceeds to reduce `cash_on_hold` and write the RELEASE transaction.
- Each call is in its own try/catch. If cancel fails, release still runs. If release fails, saga still reaches `FAILED`.
- **Double-cancel prevention (three layers):**
  1. `OrderService.cancelOrder()` returns silently for `CANCELLED` and `REJECTED` orders.
  2. Before calling cancel, `failSaga()` checks existing `SagaStep` records — if `COMPENSATION_CANCEL` already exists with status=COMPLETED, skip the call.
  3. The `orderId` is the natural DB-level idempotency key for all compensation records.
- **What if cancel step fails:**
  - Log `ERROR compensation_cancel_failed orderId={} userId={} reason={}`.
  - Continue to release hold — do not abort.
  - Record `COMPENSATION_CANCEL` SagaStep with status=FAILED.
  - Ops can manually cancel via `DELETE /orders/{id}`.
- **What if release step fails:**
  - Log `ERROR compensation_release_failed orderId={} userId={} amount={} reason={}` — include enough to manually re-run.
  - Record `COMPENSATION_RELEASE` SagaStep with status=FAILED.
  - Carol's `cash_on_hold` remains frozen. Ops can manually call `POST /ledger/release` with the orderId — the idempotency key ensures this is safe regardless of whether the automated attempt partially ran.
  - Saga reaches `FAILED`.
- **Kafka:** `order.cancelled` (if cancel ran). No new topic for release — recorded in ledger_transactions.
- **Audit:** Two SagaSteps: `COMPENSATION_CANCEL` and `COMPENSATION_RELEASE`, each with their own status and error message.
- **High volume / DB lock scenario:** `LedgerService.release()` uses `SELECT FOR UPDATE` (same as `hold()`). Under high contention, the second concurrent request for the same account row will queue behind the first. The idempotency check (`existsByOrderIdAndType`) runs inside the same `@Transactional` block, so whichever thread wins the lock checks first and the other finds the record already written. No double-release can occur.

---

**Saga status values:**

| Status | Meaning |
|--------|---------|
| `STARTED` | Saga in progress |
| `COMPLETED` | All steps succeeded |
| `COMPENSATING` | Failure detected; compensation running or pending recovery |
| `FAILED` | Compensation completed (fully or partially); saga terminal |
| `COMPENSATION_REQUIRED` | Step 4 — debit already committed; automated reversal not possible; manual action required (EQ-115) |

---

**Services Affected:**

| Service | Change |
|---------|--------|
| `saga-orchestrator` | Extend `failSaga()` with compensation steps (cancel + release); add `cancelOrder()` to `OrderClient`; COMPENSATING status + SagaRecoveryJob in EQ-113a |
| `order-service` | Add system-level cancel endpoint; make `cancelOrder()` handle terminal states per rules above |
| `ledger-service` | Add `existsByOrderIdAndType` to `LedgerTransactionRepository`; add idempotency guard to `LedgerService.release()` |

**New `OrderClient` method:**
```
POST /orders/{orderId}/system-cancel   (body: { userId })
```
Uses `saga.getUserId()` so order-service can validate the order belongs to that user. This avoids a separate unauthenticated internal endpoint while still allowing system-initiated cancellation.

**Kafka Topics:** No new topics. `order.cancelled` already exists and is published by order-service.

**Logging standards:**
All compensation log lines must include: `orderId`, `userId`, `step`, `outcome` (COMPLETED/FAILED), `reason` (on failure). Format:
```
INFO  saga_compensation step=CANCEL orderId={} userId={} outcome=COMPLETED
ERROR saga_compensation step=RELEASE orderId={} userId={} amount={} outcome=FAILED reason={}
WARN  saga_compensation_anomaly step=CANCEL orderId={} userId={} orderStatus=FILLED — manual review required
```

**API error responses:**
- `order-service`: `cancelOrder()` on a `FILLED` order returns HTTP 409 with body `{ "error": "ORDER_IN_TERMINAL_STATE", "status": "FILLED", "orderId": "..." }` — not silently ignored.
- `ledger-service`: `release()` for an already-released orderId returns HTTP 200 with current account state (idempotent success, not an error).

---

**Acceptance Criteria:**
- [ ] Step 1 failure: no compensation, saga → `FAILED`
- [ ] Step 2 failure: Cancel Order runs; saga → `FAILED`; `COMPENSATION_CANCEL` SagaStep recorded
- [ ] Step 3 failure: Cancel Order + Release Hold run independently; saga → `FAILED`; both SagaSteps recorded
- [ ] If cancel fails, release still runs; saga still reaches `FAILED`
- [ ] If release fails, saga still reaches `FAILED`; original failure reason preserved
- [ ] `OrderService.cancelOrder()` is silent no-op for `CANCELLED` and `REJECTED`; raises alert for `FILLED`
- [ ] `LedgerService.release()` is idempotent by `orderId` — second call is a no-op
- [ ] All compensation log lines include orderId, userId, step, outcome, reason

**Test Cases — `SagaCompensationTest` (unit, Mockito):**

| Scenario | Mock setup | Assert |
|----------|------------|--------|
| Compliance fails (step 1) | `complianceClient.check()` throws | saga=`FAILED`; no cancel/release called |
| Matching fails (step 2) | `orderClient.triggerMatch()` throws | saga=`FAILED`; `cancelOrder()` called; `COMPENSATION_CANCEL` step=COMPLETED |
| Debit fails (step 3) | `ledgerClient.debit()` throws | saga=`FAILED`; `cancelOrder()` + `release()` both called; both steps recorded |
| Cancel fails, release succeeds | `cancelOrder()` throws, `release()` succeeds | saga=`FAILED`; `COMPENSATION_CANCEL` step=FAILED; `COMPENSATION_RELEASE` step=COMPLETED |
| Cancel succeeds, release fails | `release()` throws | saga=`FAILED`; `COMPENSATION_CANCEL` step=COMPLETED; `COMPENSATION_RELEASE` step=FAILED |
| Cancel called on CANCELLED order | `cancelOrder()` for already-cancelled order | Silent no-op; no exception; step recorded as COMPLETED |
| Cancel called on FILLED order | `cancelOrder()` for FILLED order | WARN logged; step recorded as COMPENSATION_ANOMALY; saga still reaches `FAILED` |
| Release called twice (idempotency) | `release()` called with same orderId | Second call returns 200 with no balance change |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaCompensationTest
```

---

### EQ-115 · Saga Settlement Failure & Manual Reconciliation
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-113 |

**Scope:** Step 4 (SETTLEMENT_CREATE) failure handling. Currently unhandled — a settlement failure silently falls through to `COMPLETED`, leaving the system in an inconsistent state: debit committed, settlement record absent.

---

**Why this is separate from EQ-113:**

Step 4 failure is fundamentally different from steps 2–3. By the time settlement fails:
- The debit has already committed — `cash_balance` is already reduced.
- Cancel Order and Release Hold (from EQ-113) do NOT restore the money. `release()` reduces `cash_on_hold`, not `cash_balance`. Calling release() after a successful debit has no financial effect.
- What is actually needed is a **credit transaction** — a new `ledger_transactions` entry that adds the amount back to `cash_balance`. This touches the financial ledger in a way that requires controls (reconciliation, sign-off) beyond automated saga logic.

**Scenario 4 — Settlement fails (concrete example):**

David's 10-share AAPL buy order matched at $190/share. The saga executed:
- Step 1 ✓ Compliance passed
- Step 2 ✓ Order matched, filled at $190 → order status `FILLED`
- Step 3 ✓ Debit ran — David's `cash_balance` reduced by $1,900. His `cash_on_hold` also reduced (the hold consumed by the debit).
- Step 4 ✗ Settlement service is down. Feign call throws.

At this point:
- `cash_balance` is $1,900 lower. This is the source of truth for David's cash.
- `cash_on_hold` was already reduced by the debit — it does not have $1,900 frozen.
- Calling `release()` here would try to reduce `cash_on_hold` which is already reduced — it would floor at zero with no meaningful effect. It does not restore `cash_balance`.
- Calling `cancelOrder()` on a `FILLED` order is an anomaly (as defined in EQ-113) — it should not be done.

**What this means operationally:**
David is missing $1,900 from his balance with no settlement record. The correct fix is a CREDIT of $1,900 back to his `cash_balance`. This is a new ledger transaction, not a reversal of an existing one. It requires:
1. Manual review to confirm the settlement actually did not happen (settlement-service might have written the record before crashing)
2. Manual call to `POST /ledger/credit` (new endpoint) or direct DB operation with audit sign-off

**Compensation for step 4:**
- Do NOT call `cancelOrder()` — order is FILLED. Log the anomaly instead.
- Do NOT call `release()` — hold was consumed by the debit. No effect.
- Set `saga.status = COMPENSATION_REQUIRED`
- Log: `CRITICAL saga_compensation_required orderId={} userId={} amount={} reason=settlement_failure`
- Publish Kafka event `saga.compensation.required` → consumed by audit-service for ops alerting

**Step 4 failure detection gap (existing bug):**
The current code has no `if ("FAILED".equals(step4.getStatus()))` guard. Step 4 falls through to `saga.setStatus("COMPLETED")`. The first change in this ticket is adding that guard.

---

**New Kafka Topic:**

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `saga.compensation.required` | `saga-orchestrator` | `audit-service` | Ops alerting — manual financial reconciliation required |

**API changes:**
- New `POST /ledger/credit` endpoint on ledger-service for manual reconciliation use by ops (not automated). Requires admin role. Records a CREDIT ledger transaction and increases `cash_balance`.

**Acceptance Criteria:**
- [ ] Step 4 failure is detected (guard added); saga no longer falls through to COMPLETED
- [ ] Step 4 failure sets `saga.status = COMPENSATION_REQUIRED`
- [ ] `saga.compensation.required` Kafka event published with orderId, userId, amount, reason
- [ ] `audit-service` consumes the event and records it
- [ ] `cancelOrder()` is NOT called for step 4 (order is FILLED)
- [ ] `release()` is NOT called for step 4 (hold was consumed by debit)
- [ ] New `POST /ledger/credit` endpoint available for manual use (admin role)

**Test Cases:**

| Scenario | Assert |
|----------|--------|
| Settlement fails | saga=`COMPENSATION_REQUIRED`; no cancel called; no release called; Kafka event published |
| Settlement fails — audit-service receives event | audit record created with orderId + amount |

```bash
mvn test -pl saga-orchestrator -Dtest=SagaCompensationTest#settlement_fails_setsCompensationRequired
mvn test -pl audit-service -Dtest=AuditServiceTest#sagaCompensationRequired_recordsAuditEntry
```

---

### EQ-116a · Unit Idempotency Tests
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 2 | P0 — depends on EQ-113b |

**Purpose:** Add idempotency test cases to existing unit test files. No new infrastructure — these are Mockito tests that extend files already present in each service.

**Why separate from EQ-116b:**
These tests run fast, require no containers, and provide the first safety net for the idempotency changes introduced in EQ-113b. They should be written and passing before the heavyweight integration suite.

**Test cases to add:**

| File | Method | Scenario | Assert |
|------|--------|----------|--------|
| `LedgerServiceTest` | `release_duplicateOrderId_isNoOp` | `release()` called with same orderId twice | Balance unchanged on second call; RELEASE tx exists exactly once |
| `OrderServiceTest` | `cancelOrder_alreadyCancelled_isNoOp` | `cancelOrder()` for already-CANCELLED order | Returns silently; no DB write; no exception |
| `OrderServiceTest` | `cancelOrder_rejectedOrder_isNoOp` | `cancelOrder()` for REJECTED order | Returns silently; no DB write; no exception |
| `OrderServiceTest` | `cancelOrder_filledOrder_logsAnomaly` | `cancelOrder()` for FILLED order | WARN logged; HTTP 409 returned; no status change |

```bash
mvn test -pl ledger-service -Dtest=LedgerServiceTest#release_duplicateOrderId_isNoOp
mvn test -pl order-service -Dtest=OrderServiceTest
```

---

### EQ-116b · Saga Data Integrity Test Suite
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 3 | P0 — depends on EQ-115, EQ-116a |

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
