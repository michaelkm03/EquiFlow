# EquiFlow — Product & Engineering Backlog
**Version:** 1.1
**Status:** Approved
**Product Owner:** Michael Montgomery
**Engineering Lead:** Michael Montgomery
**Last Updated:** 2026-03-16

---

## Sign-Off

| Role | Name | Date |
|------|------|------|
| Product Owner | Michael Montgomery | 2026-03-16 |
| Engineering Lead | Michael Montgomery | 2026-03-16 |

> Stories in **Sprint 1** are approved and scheduled. Stories in the **Backlog**
> are approved by product and engineering but not yet assigned to a sprint.
> All feature stories have been reviewed for technical feasibility by the
> engineering lead.

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
**Epic:** Order Management
**Type:** Feature
**Points:** 5
**Priority:** P0

**Product Request:**
> "Users are asking for downside protection. We need stop-loss orders so they
> can set a trigger price and automatically exit a position if it drops to that
> level. This is table stakes for any retail trading platform."
> — Product, 2026-03-10

**Background:**
Currently the engine supports market and limit orders only. A stop-loss order
sits as a passive order until the market price of a given ticker falls to or
below a configured trigger price, at which point it converts to a market order
and executes.

**Acceptance Criteria:**
- [ ] `POST /orders` accepts `"type": "STOP_LOSS"` with a `triggerPrice` field
- [ ] Order is stored with status `PENDING_TRIGGER` and does not enter the matching engine immediately
- [ ] `MarketDataService` price updates are evaluated against all open stop-loss orders
- [ ] When market price ≤ `triggerPrice`, order converts to a market order and is submitted for execution
- [ ] If trigger fires outside NYSE hours, order is queued for next market open
- [ ] Triggered and filled stop-loss orders appear in order history with both trigger and fill prices

**Files:**
`order-service/`, `market-data-service/`

---

### EQ-102 · Order History — Filtering and Pagination
**Epic:** Order Management
**Type:** Feature
**Points:** 3
**Priority:** P0

**Product Request:**
> "Right now users get a flat dump of every order they've ever placed. We need
> date range filtering and pagination — the list is unusable once a user has
> more than a few dozen orders."
> — Product, 2026-03-08

**Background:**
`GET /orders` currently returns all orders for the authenticated user with no
filtering or pagination. This needs to support date range, status, and ticker
filtering with cursor-based or offset pagination.

**Acceptance Criteria:**
- [ ] `GET /orders` supports query params: `from`, `to` (ISO 8601 dates), `status`, `ticker`, `page`, `size`
- [ ] Default page size is 25; max is 100
- [ ] Response includes a `pagination` object with `totalElements`, `totalPages`, `currentPage`
- [ ] Invalid date format returns HTTP 400 with a descriptive error
- [ ] Filtering by `status=FILLED` returns only filled orders

**Files:**
`order-service/src/main/java/com/equiflow/order/`

---

### EQ-103 · Portfolio P&L Summary Endpoint
**Epic:** Portfolio Analytics
**Type:** Feature
**Points:** 5
**Priority:** P1

**Product Request:**
> "Users have no way to see whether they're up or down overall. We need a
> portfolio summary — current value, total cost basis, and unrealized P&L per
> position. This is the most-requested feature from beta users."
> — Product, 2026-03-05

**Background:**
`LedgerService` tracks positions with weighted average cost basis per ticker.
`MarketDataService` holds current prices. A new `/portfolio/summary` endpoint
should join these to calculate unrealized P&L per position and in aggregate.

**Acceptance Criteria:**
- [ ] `GET /portfolio/summary` returns a list of open positions
- [ ] Each position includes: `ticker`, `quantity`, `avgCostBasis`, `currentPrice`, `marketValue`, `unrealizedPnl`, `unrealizedPnlPct`
- [ ] Response includes an aggregate: `totalCostBasis`, `totalMarketValue`, `totalUnrealizedPnl`
- [ ] Current price is sourced from `MarketDataService` in real time
- [ ] A position with zero quantity is excluded from the response

**Files:**
`ledger-service/`, `market-data-service/`

---

### EQ-104 · Price Alerts — Notify When Target Price Is Hit
**Epic:** Notifications
**Type:** Feature
**Points:** 5
**Priority:** P1

**Product Request:**
> "Users want to know when a stock hits their target price without having to
> watch the screen all day. Add price alerts — let them set a target and get
> notified when the price crosses it."
> — Product, 2026-03-11

**Background:**
Users should be able to configure a price alert on any ticker. When
`MarketDataService` records a price crossing the alert threshold, an event is
published to Kafka and consumed by a new notification handler that logs or
delivers the alert.

**Acceptance Criteria:**
- [ ] `POST /alerts` accepts `{ ticker, targetPrice, direction: "ABOVE" | "BELOW" }`
- [ ] `GET /alerts` returns all active alerts for the authenticated user
- [ ] `DELETE /alerts/{id}` cancels an alert
- [ ] When market price crosses the threshold, a `price.alert.triggered` Kafka event is published
- [ ] Triggered alert is marked `TRIGGERED` and excluded from future evaluations

**Files:**
`market-data-service/`, new `notification-service/` or alert handler within `market-data-service/`

---

## Platform Sprint 1 — Engineering
**Sprint Goal:** Establish CI and fix correctness gaps before feature work ships.
> These are internal engineering tasks. They are not user-facing but are
> required for the team to ship features reliably.

---

### EQ-110 · CI Pipeline (GitHub Actions)
**Epic:** Platform
**Type:** Engineering
**Points:** 2
**Priority:** P0

**Acceptance Criteria:**
- [ ] Workflow triggers on `push` and `pull_request` to `master`
- [ ] Pipeline runs `mvn --batch-mode test` across all modules on Java 21
- [ ] A failing test causes the pipeline to report failure
- [ ] README displays CI status badge

**Files:** `.github/workflows/ci.yml` *(new)*

---

### EQ-111 · Fix Java Version Mismatch
**Epic:** Platform
**Type:** Engineering
**Points:** 1
**Priority:** P0 — blocks EQ-110

**Acceptance Criteria:**
- [ ] `pom.xml` declares Java 21; README updated to match
- [ ] Build and all tests pass under Java 21 in CI

---

### EQ-112 · LedgerService Test Coverage
**Epic:** Platform
**Type:** Engineering
**Points:** 5
**Priority:** P0

**Acceptance Criteria:**
- [ ] Hold, debit, release, and insufficient funds paths all have coverage
- [ ] Concurrent debit on the same account produces a consistent final balance
- [ ] Coverage for `LedgerService` reaches ≥ 80%

---

### EQ-113 · Saga Compensation / Rollback
**Epic:** Platform
**Type:** Engineering
**Points:** 8
**Priority:** P0

**Acceptance Criteria:**
- [ ] `failSaga()` calls `releaseHold()` on `LedgerService` if debit step was reached
- [ ] `failSaga()` calls `cancelOrder()` on `OrderService` if matching step was reached
- [ ] Compensation steps are idempotent

---

## Backlog — Approved, Not Yet Scheduled

---

### EQ-201 · Trade Confirmation Documents
**Epic:** Compliance & Reporting
**Type:** Feature
**Points:** 5

**Product Request:**
> "Regulatory requirement. Every executed trade needs a confirmation document
> showing the user what was traded, at what price, and what fees were applied.
> Must be available for download within 24 hours of the trade."

**Acceptance Criteria:**
- [ ] `GET /orders/{id}/confirmation` returns a structured confirmation for any `FILLED` order
- [ ] Confirmation includes: order ID, ticker, type, quantity, fill price, timestamp, estimated fee
- [ ] `AuditService` logs every confirmation access
- [ ] Requesting a confirmation for a non-filled order returns HTTP 409


---

### EQ-202 · Account Funding — Deposit and Withdrawal
**Epic:** Account Management
**Type:** Feature
**Points:** 8

**Product Request:**
> "Users can't do anything useful until they can fund their account. We need
> deposit and withdrawal flows — even if the actual bank integration is mocked
> for now, the API and ledger side must be fully implemented."

**Acceptance Criteria:**
- [ ] `POST /accounts/deposit` increases available balance and creates a ledger entry
- [ ] `POST /accounts/withdraw` decreases available balance if sufficient funds exist
- [ ] Withdrawal below available balance returns HTTP 422
- [ ] All deposit and withdrawal events published to Kafka and logged by `AuditService`
- [ ] Bank integration is stubbed/mocked; real ACH integration is out of scope for this story

---

### EQ-203 · Wash-Sale Compliance — User-Facing Warning
**Epic:** Compliance & Reporting
**Type:** Feature
**Points:** 3

**Product Request:**
> "Right now wash-sale violations are silently blocked. Users don't understand
> why their order was rejected. We need a clear explanation returned with the
> rejection so users can make an informed decision."

**Acceptance Criteria:**
- [ ] Wash-sale rejection response includes a `complianceReason` field explaining the rule
- [ ] Response includes `blockedUntil` date (30 days after the triggering sale)
- [ ] The original sale that triggered the rule is referenced by order ID in the response
