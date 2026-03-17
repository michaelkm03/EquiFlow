# EquiFlow — SE Contribution Plan

This document tracks planned contributions to demonstrate software engineering
ability across testing, architecture, CI/CD, and code quality.

---

## Required (Must-Do — Core SE Signal)

### 1. Fix Dead Test in OrderService
**File:** `order-service/src/test/java/com/equiflow/order/OrderServiceTest.java`

`testMarketHoursValidatorWeekend()` currently asserts `result || !result` — a
tautology that always passes and tests nothing. Rewrite it to assert that an
order submitted on a Saturday or Sunday is rejected with the appropriate error.

**Why it matters:** This is the first thing any senior engineer reviewer flags.
A no-op test is worse than no test — it gives false confidence.

**Tasks:**
- [ ] Set test clock to a Saturday datetime
- [ ] Submit a market order
- [ ] Assert response is an error / exception with a message referencing market hours
- [ ] Repeat for Sunday

---

### 2. Write Tests for LedgerService
**File:** `ledger-service/src/test/java/com/equiflow/ledger/` *(create)*

Zero tests exist for the highest-risk service in the system — the one
responsible for `SELECT FOR UPDATE` financial debit/hold logic. This is the
most important gap in the repo.

**Tasks:**
- [ ] Unit test: successful hold reduces available balance
- [ ] Unit test: debit below zero throws InsufficientFundsException
- [ ] Unit test: releasing a hold restores available balance
- [ ] Integration test (`@SpringBootTest`): concurrent debit on same account
  serializes correctly (two threads, one should fail or block)
- [ ] Integration test: hold → debit → verify final balance is correct

---

### 3. Write Integration Tests for OrderService
**File:** `order-service/src/test/java/com/equiflow/order/OrderServiceIntegrationTest.java` *(create)*

Use `@SpringBootTest` with H2 (in-memory) or Testcontainers (PostgreSQL) to
test the full order submission flow end-to-end within the service boundary.

**Tasks:**
- [ ] Market order success — assert order persisted with PENDING status
- [ ] Limit order placement — assert price and quantity stored correctly
- [ ] Order cancellation — assert status transitions to CANCELLED
- [ ] NYSE hours rejection — assert orders outside market hours are rejected
- [ ] Duplicate order ID handling

---

### 4. Implement Saga Compensation / Rollback
**File:** `saga-orchestrator/src/main/java/com/equiflow/saga/OrderSaga.java`

`failSaga()` currently only logs and marks the saga FAILED. It does not
compensate — no ledger hold is released, no partial compliance result is voided,
no order is cancelled. This breaks the fundamental guarantee of the saga pattern.

**Tasks:**
- [ ] Add `releaseHold()` compensation call to LedgerService if debit step
  was reached before failure
- [ ] Add `cancelOrder()` compensation call to OrderService on failure
- [ ] Add a `compensate()` method that executes cleanup steps in reverse order
- [ ] Write a test that fails the saga mid-way and asserts compensation ran

---

### 5. Add GitHub Actions CI Pipeline
**File:** `.github/workflows/ci.yml` *(create)*

No automated build or test exists on any push or PR. This is the single most
visible SE signal to any engineer reviewing the repo.

**Tasks:**
- [ ] Create workflow triggered on `push` and `pull_request` to `master`
- [ ] Step: checkout code
- [ ] Step: set up Java 17 (or 21 — fix version mismatch first, see #6)
- [ ] Step: `mvn --batch-mode test` across all modules
- [ ] Step: publish test results as workflow artifact
- [ ] (Optional) Add a badge to README

```yaml
# .github/workflows/ci.yml skeleton
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'
      - run: mvn --batch-mode test
```

---

### 6. Fix Java Version Discrepancy
**Files:** `pom.xml`, `README.md`

`pom.xml` declares `<java.version>17</java.version>` but the README and project
description claim Java 21. Every engineer reviewing the repo will notice this.

**Tasks:**
- [ ] Decide on Java 21 (preferred — aligns with README and modern Spring Boot 3.x)
- [ ] Update `pom.xml`: `<java.version>21</java.version>`
- [ ] Update `<source>` and `<target>` compiler plugin settings if present
- [ ] Verify the project builds and tests pass under Java 21

---

## Nice to Have (Differentiators)

### 7. Add Bean Validation to DTOs
**Files:** Request DTO classes across all services

Replace manual null/range checks inside service methods with standard
`@NotNull`, `@Valid`, `@Min`/`@Max` annotations on request DTOs. Spring Boot
will enforce these automatically via `@Valid` on controller parameters.

**Tasks:**
- [ ] Add `jakarta.validation` annotations to OrderRequest, AccountRequest, etc.
- [ ] Add `@Valid` to corresponding `@PostMapping` / `@PutMapping` controller args
- [ ] Write a test asserting a 400 response when a required field is missing

---

### 8. Write Wash-Sale Boundary Condition Tests
**File:** `compliance-service/src/test/java/com/equiflow/compliance/`

The wash-sale rule has a precise 30-day window. Boundary tests prove the
implementation is correct at the edges, not just in the happy path.

**Tasks:**
- [ ] Test: sell today, buy tomorrow — assert BLOCKED
- [ ] Test: sell 30 days ago, buy today — assert BLOCKED (within window)
- [ ] Test: sell 31 days ago, buy today — assert ALLOWED
- [ ] Test: sell and buy different ticker — assert ALLOWED

---

### 9. Add JMeter or Gatling Performance Test
**Directory:** `performance/` *(create)*

The SPEC calls for 5,000 concurrent order tests. Even a committed test plan
demonstrates performance engineering discipline.

**Tasks:**
- [ ] Create a JMeter `.jmx` test plan targeting the order submission endpoint
- [ ] Configure thread group: 100 users, 50 orders each
- [ ] Add latency assertions: p99 < 200ms
- [ ] Add a README in `performance/` describing how to run it
- [ ] (Optional) Add a Gatling Scala simulation as an alternative

---

### 10. Persist OrderBook State Across Restarts
**File:** `order-service/src/main/java/com/equiflow/order/OrderBook.java`

The in-memory `TreeMap`-based order book is wiped on any service restart.
Add serialization on shutdown and reload on startup, or document the tradeoff
explicitly.

**Tasks:**
- [ ] Option A: Add `@PreDestroy` to serialize order book to Redis or DB,
  `@PostConstruct` to reload
- [ ] Option B: Add a prominent comment in `OrderBook.java` documenting the
  deliberate tradeoff (simulation scope, not production)
- [ ] Option B is acceptable for a portfolio project — just make it explicit

---

## Summary Checklist

| # | Task | Type | Status |
|---|------|------|--------|
| 1 | Fix dead test in OrderServiceTest | Required | [ ] |
| 2 | Write LedgerService tests | Required | [ ] |
| 3 | Write OrderService integration tests | Required | [ ] |
| 4 | Implement saga compensation/rollback | Required | [ ] |
| 5 | Add GitHub Actions CI pipeline | Required | [ ] |
| 6 | Fix Java 17 vs 21 version mismatch | Required | [ ] |
| 7 | Add Bean Validation to DTOs | Nice to Have | [ ] |
| 8 | Write wash-sale boundary tests | Nice to Have | [ ] |
| 9 | Add JMeter/Gatling performance tests | Nice to Have | [ ] |
| 10 | Persist OrderBook state or document tradeoff | Nice to Have | [ ] |
