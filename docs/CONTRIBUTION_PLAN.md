# EquiFlow — Product & Engineering Backlog
**Version:** 1.2
**Status:** Approved
**Product Owner:** Claude
**Engineering Lead:** Michael Montgomery
**Last Updated:** 2026-03-25

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
| ⚪ | <nobr>[EQ-112](#eq-112--ledgerservice-test-coverage)</nobr> | LedgerService Test Coverage — hold, debit, release, concurrency paths | 5 | P0 |
| ⚪ | <nobr>[EQ-113](#eq-113--saga-compensation--rollback)</nobr> | Saga Compensation — rollback ledger and order on saga failure | 8 | P0 |
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
ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceTest.java
ledger-service/src/test/java/com/equiflow/ledger/LedgerServiceConcurrencyTest.java
```

This follows the same convention used by every other service in the project:

| Service | Test File |
|---------|-----------|
| `auth-service` | `src/test/java/com/equiflow/auth/AuthServiceTest.java` |
| `order-service` | `src/test/java/com/equiflow/order/OrderServiceTest.java` |
| `compliance-service` | `src/test/java/com/equiflow/compliance/ComplianceServiceTest.java` |
| `audit-service` | `src/test/java/com/equiflow/audit/AuditServiceTest.java` |
| `settlement-service` | `src/test/java/com/equiflow/settlement/SettlementServiceTest.java` |
| `saga-orchestrator` | `src/test/java/com/equiflow/saga/SagaOrchestratorTest.java` |
| `ledger-service` | `src/test/java/com/equiflow/ledger/LedgerServiceTest.java` ← **this ticket** |

`LedgerService` is the only service in the project with no test file. `LedgerServiceConcurrencyTest` is the only second test file across any service — justified because concurrency requires a real Postgres container and must be kept separate from the Mockito unit tests that run without any infrastructure.

All existing tests use the same structure: `@BeforeMethod` sets up mocks, `@Test` methods use TestNG assertions and `Mockito.verify`. `LedgerServiceTest` must follow this exact pattern.

**Database Changes:** None — unit tests use Mockito; concurrency tests require Testcontainers (real Postgres needed for `SELECT FOR UPDATE` semantics — H2 does not enforce row-level locking)

**Test Cases — `LedgerServiceTest` (unit, Mockito)**

| Method | Scenario | Assert |
|--------|----------|--------|
| `hold` | Normal hold on $100,000 account for $1,500 | `cashOnHold` = $1,500; `availableCash` = $98,500; HOLD transaction saved |
| `hold` | Hold amount exactly equals `availableCash` (boundary) | Succeeds; `availableCash` = $0 |
| `hold` | Amount exceeds `availableCash` | `IllegalStateException`: "Insufficient available funds"; balance unchanged |
| `release` | Release $1,500 after a hold is placed | `cashOnHold` = $0; RELEASE transaction saved |
| `release` | Called with no existing hold (`cashOnHold` = $0) | Floors to zero; no exception — safe for saga compensation calls |
| `debit` | Debit $1,500 with matching hold already placed | `cashBalance` -= $1,500; `cashOnHold` -= $1,500; DEBIT transaction saved |
| `debit` | `ticker` + `quantity` provided | `updatePosition` invoked; new position created with correct `avgCost` |
| `debit` | No `ticker` (cash-only settlement) | No position upserted; only balance updated |
| `debit` | `cashBalance` < requested amount | `IllegalStateException`: "Insufficient funds"; balance unchanged |
| `updatePosition` | Second BUY on existing position | Quantity sums; `avgCost` recalculated as weighted average |
| `updatePosition` | SELL reduces position | Quantity decreases; floors at zero if oversold |
| `getAccount` | User ID has no account | `IllegalArgumentException` thrown |

**Test Cases — `LedgerServiceConcurrencyTest` (integration, Testcontainers Postgres)**

| Scenario | Assert |
|----------|--------|
| Two threads hold $800 simultaneously on a $1,000 account | Exactly one succeeds; one throws `IllegalStateException`; final `cashOnHold` = $800 |
| Two threads debit $600 simultaneously on a $1,000 account | One succeeds; one throws; final `cashBalance` = $400; no lost update |

**Acceptance Criteria:**
- [ ] All 12 unit test cases pass with Mockito (no Spring context, no DB)
- [ ] Both concurrency tests pass against a real Postgres container
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

### EQ-113 · Saga Compensation / Rollback
| Epic | Type | Points | Priority |
|------|------|--------|----------|
| Platform | Engineering | 8 | P0 |

**Services Affected:**

| Service | Change |
|---------|--------|
| `saga-orchestrator` | Add `compensate()` method to `OrderSaga`; invoke rollback steps in reverse order on failure |
| `ledger-service` | `releaseHold()` must be idempotent — safe to call if no hold exists |
| `order-service` | `cancelOrder()` must be idempotent — safe to call on an already-cancelled order |

**Kafka Topics:** No new topics; compensation actions are synchronous Feign calls.

**Acceptance Criteria:**
- [ ] `failSaga()` calls `releaseHold()` on `LedgerService` if the debit step was reached
- [ ] `failSaga()` calls `cancelOrder()` on `OrderService` if the matching step was reached
- [ ] Compensation steps are idempotent — calling twice does not double-release or error

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
