# EquiFlow

[![CI](https://github.com/michaelkm03/EquiFlow/actions/workflows/ci.yml/badge.svg)](https://github.com/michaelkm03/EquiFlow/actions/workflows/ci.yml)

A high-integrity US equity trading engine built with Java 21 and Spring Boot 3.x microservices. Simulates a production-grade brokerage backend with real order book matching, SEC/FINRA compliance enforcement, Saga-orchestrated distributed transactions, and chaos engineering support.

> **Local development only.** No cloud deployment required.

---

## Features

- **Order book** — real bid/ask matching with price-time priority. Market orders fill via existing limits then market maker. Limit orders queue and expire at 4 PM ET.
- **Stop-loss orders** — price-triggered sell/buy orders. Submitted with a `triggerPrice`; held as `PENDING_TRIGGER` until market data crosses the threshold, then executed as a market order via the matching engine.
- **Compliance** — wash-sale hard block (30-day window) and insufficient funds check before any order executes.
- **T+1 Settlement** — scheduled job at market close, business-day aware with 2026 NYSE holiday calendar.
- **Regulatory audit log** — append-only, PostgreSQL row-security enforced. No UPDATE or DELETE permitted.
- **Chaos engineering** — inject network latency and DB failures mid-Saga via admin API to test recovery.
- **Market scenarios** — scripted price events (flash crash, bull surge, volatility spike) triggerable via API.
- **Swagger UI** — all 9 services aggregated at `http://localhost:8080/swagger-ui.html`.

---

## Services & Ports

| Service | Port | Responsibility |
|---|---|---|
| api-gateway | 8080 | Routing, Swagger aggregation |
| auth-service | 8081 | JWT issuance, user management |
| order-service | 8082 | Order book, matching engine |
| market-data-service | 8083 | Simulated price feed, scenarios |
| compliance-service | 8084 | Wash-sale + funds checks |
| ledger-service | 8085 | Balances, positions, holds |
| settlement-service | 8086 | T+1 scheduled settlement |
| audit-service | 8087 | Append-only event log |
| saga-orchestrator | 8088 | Distributed transaction coordinator |
| surge-simulator | 8089 | Chaos injection |
| kafdrop | 9000 | Kafka topic browser (dev tool) |

---

## Prerequisites

### Mac
```bash
# Install Homebrew if not already installed
/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"

brew install --cask docker          # Docker Desktop
brew install openjdk@21             # Java 21
brew install maven                  # Maven
brew install node                   # Node.js (for Playwright)
npm install -g allure-commandline   # Allure CLI
```

After installing Java 21 on Mac, add to your shell profile (`~/.zshrc` or `~/.bash_profile`):
```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
export PATH=$JAVA_HOME/bin:$PATH
```

### Windows
1. **Docker Desktop** — download from [docker.com](https://www.docker.com/products/docker-desktop). Enable WSL 2 backend when prompted.
2. **Java 21** — download from [Adoptium](https://adoptium.net/). Run the installer; it sets `JAVA_HOME` automatically.
3. **Maven** — download from [maven.apache.org](https://maven.apache.org/download.cgi). Extract and add `bin/` to your `PATH` in System Environment Variables.
4. **Node.js** — download from [nodejs.org](https://nodejs.org/) (LTS version).
5. **Allure CLI** — run in PowerShell: `npm install -g allure-commandline`

Verify all installations:
```bash
docker --version   # Docker 24+
java --version     # openjdk 21
mvn --version      # Apache Maven 3.9+
node --version     # v20+
```

---

## Local Setup

### 1. Clone and configure

```bash
git clone <your-repo-url>
cd equiflow
cp .env.example .env
```

The default `.env` values work out of the box — no changes needed for local development.

### 2. Start the full stack

```bash
docker-compose up --build
```

First run takes ~3–5 minutes to pull images and build all services. Subsequent starts are faster.

All services auto-apply Flyway migrations and seed users on first boot.

**To stop:**
```bash
# Stop containers (data is preserved):
docker-compose stop

# Stop and destroy all containers + wipe all data:
docker-compose down
```

### 3. Verify everything is running

```bash
# Check all containers are healthy
docker ps

# Test the gateway
curl http://localhost:8080/actuator/health
```

Open Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## Seed Data

All data is seeded automatically by Flyway on first boot — no manual setup required.

**Users** (all passwords: `password123`):

| Username | Role | Ledger Account | UUID |
|---|---|---|---|
| `trader1` | TRADER | $100,000 cash | `a1000000-0000-0000-0000-000000000001` |
| `trader2` | TRADER | $100,000 cash | `a1000000-0000-0000-0000-000000000004` |
| `regulator1` | REGULATOR | none | `a1000000-0000-0000-0000-000000000002` |
| `bot-operator1` | BOT_OPERATOR | none | `a1000000-0000-0000-0000-000000000003` |

**Market data:** 30 tickers pre-seeded with realistic baseline prices (AAPL, MSFT, TSLA, NVDA, GOOGL, AMZN and 24 more).

**Everything else** (orders, compliance checks, sagas, audit events, settlements) starts empty and is populated as you make API calls.

### Get a token
```bash
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username": "trader1", "password": "password123"}'
```

---

## Postman Collection

A fully documented Postman collection is included at [`equiflow-postman-collection.json`](equiflow-postman-collection.json).

**Import:** Postman → File → Import → select the file.

**Folders:**

| Folder | Contents |
|---|---|
| `00 - Setup & Auth` | Login requests for each user — run these first to populate tokens |
| `01–10` — service folders | Every endpoint with example request bodies and responses |
| `11 - Workflows` | Pre-built flows: single trade lifecycle, regulator oversight, chaos test |

**Collection variables** auto-populated by the setup folder:

| Variable | Description |
|---|---|
| `base_url` | `http://localhost:8080` (edit to change environment) |
| `trader1_token` | JWT for trader1, auto-saved by setup login |
| `trader2_token` | JWT for trader2, auto-saved by setup login |
| `regulator1_token` | JWT for regulator1, auto-saved by setup login |
| `botoperator1_token` | JWT for bot-operator1, auto-saved by setup login |
| `order_id` | Auto-saved when you POST a new order |
| `saga_id` | Auto-saved when you look up a saga |

**Quick start:** run folder `00 - Setup & Auth` top-to-bottom (4 requests), then use any workflow or individual endpoint.

---

## Quick Start — Place a Trade

```bash
# 1. Get token
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"password123"}' | jq -r .token)

# 2. Submit a market order
curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ticker":"AAPL","side":"BUY","type":"MARKET","quantity":10}'

# 3. Check order status (replace ORDER_ID)
curl http://localhost:8080/orders/{ORDER_ID} \
  -H "Authorization: Bearer $TOKEN"

# 4. View your portfolio
curl http://localhost:8080/ledger/accounts/{USER_ID} \
  -H "Authorization: Bearer $TOKEN"
```

---

## Stop-Loss Orders

Stop-loss orders are held pending until the market price crosses a trigger threshold, then automatically executed as market orders.

### Submit a stop-loss order

```bash
curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ticker":"AAPL","side":"SELL","type":"STOP_LOSS","quantity":10,"triggerPrice":170.00}'
```

The order is saved with status `PENDING_TRIGGER` and passes compliance via the saga — it does not enter the matching engine until the trigger fires.

### How triggering works

1. **market-data-service** calls `POST /orders/internal/stop-loss/evaluate` on every price tick and on each scenario step.
2. **order-service** (`StopLossService`) finds `PENDING_TRIGGER` orders where `triggerPrice >= currentPrice`, marks them `TRIGGERED`, and publishes to Kafka topic `equiflow.order.stop-loss.triggered`.
3. **saga-orchestrator** consumes the event, overrides the order type to `MARKET`, and re-runs the saga through matching, ledger debit, and settlement.

### Test stop-loss triggering

Run a flash crash scenario to drive prices down across all tickers:

```bash
curl -X POST http://localhost:8080/admin/market/scenarios/flash_crash/start \
  -H "Authorization: Bearer $BOT_TOKEN"
```

Or simulate a single price tick:

```bash
curl -X POST http://localhost:8080/market/tickers/AAPL/tick \
  -H "Authorization: Bearer $BOT_TOKEN"
```

---

## Running Tests

### Unit & Integration Tests

```bash
# All unit tests (no stack required)
mvn test

# All integration tests
mvn verify
```

### E2E Tests (Playwright)

Requires the full Docker stack to be running.

```bash
cd tests/e2e
npm install
npx playwright test

# View report
npx playwright show-report
```

### Performance Tests (JMeter)

Requires the full Docker stack to be running.

```bash
# Run via JMeter GUI
jmeter -t tests/performance/equiflow-load-test.jmx

# Run headless
jmeter -n -t tests/performance/equiflow-load-test.jmx -l results.jtl
```

### Allure Report

```bash
mvn verify                          # generates allure-results
allure serve target/allure-results  # opens visual dashboard
```

### Agent Tests (Python)

Requires Python 3.11+ and the `equiflow-mcp` dependencies. No Docker stack required for Tier 1 or Tier 2.

```bash
cd equiflow-mcp
pip install -r requirements-test.txt

# Run all tests (Tier 1 unit + Tier 2 behavioral)
pytest tests/ -v

# Run one tier at a time
pytest tests/test_loop.py tests/test_handlers.py -v          # Tier 1 only
pytest tests/test_compliance_agent_behavior.py -v            # Tier 2 only
```

**Tier 3 — golden file replay** (requires Docker stack running + `ANTHROPIC_API_KEY` set):

```bash
# Record once — hits real Anthropic API and saves the full conversation to a fixture file
python equiflow-mcp/tests/record_golden.py "Show me today's compliance breaches"

# Replay — no API call, fully deterministic; run before major prompt or model changes
pytest equiflow-mcp/tests/test_compliance_agent_e2e.py -v
```

See [Agent Testing Architecture](#agent-testing-architecture) for details on what each tier covers and when to run it.

---

## Chaos Engineering

Trigger failures mid-Saga to test system recovery:

```bash
BOT_TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"bot-operator1","password":"password123"}' | jq -r .token)

# Start chaos (network latency + 30% DB failure rate)
curl -X POST http://localhost:8080/admin/chaos/start \
  -H "Authorization: Bearer $BOT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"mode":"BOTH","latencyMs":2000,"failureRatePercent":30,"triggeredBy":"bot-operator1"}'

# Check status
curl http://localhost:8080/admin/chaos/status \
  -H "Authorization: Bearer $BOT_TOKEN"

# Stop chaos
curl -X POST http://localhost:8080/admin/chaos/stop \
  -H "Authorization: Bearer $BOT_TOKEN"
```

---

## Kafka — Tracing Messages

**Kafdrop** is included as a dev tool — a browser UI for browsing all Kafka topics and messages.

Open **http://localhost:9000** after `docker-compose up`.

From Kafdrop you can:
- See all `equiflow.*` topics and their message counts
- Click into any topic and browse individual messages as formatted JSON
- Filter messages by offset to isolate a specific scenario run
- Watch the order flow end-to-end across topics

**Topics produced during a scenario:**

| Topic | Producer | What it carries |
|---|---|---|
| `equiflow.order.placed` | order-service | Every new order with userId, ticker, side, qty, price, status |
| `equiflow.order.filled` | order-service | Order fill events |
| `equiflow.order.cancelled` | order-service | Order cancellation events |
| `equiflow.compliance.approved` | compliance-service | Orders that passed compliance check |
| `equiflow.order.stop-loss.triggered` | order-service | Stop-loss trigger fired |

**Tip for scenario tracing:** note the message offset in Kafdrop before you run the seed script, then use "Jump to offset" after to see only messages from your run.

---

## Market Scenarios

```bash
# Trigger a flash crash
curl -X POST http://localhost:8080/admin/market/scenarios/flash_crash/start \
  -H "Authorization: Bearer $BOT_TOKEN"

# Available scenarios: flash_crash, bull_run, bear_market,
#                      high_volatility, sector_rotation, liquidity_crisis

# Stop active scenario
curl -X POST http://localhost:8080/admin/market/scenarios/stop \
  -H "Authorization: Bearer $BOT_TOKEN"
```

---

## Project Structure

```
equiflow/
├── api-gateway/          # Spring Cloud Gateway + Swagger aggregator
├── auth-service/         # JWT auth + user management
├── order-service/        # Order book + matching engine
├── market-data-service/  # Simulated price feed + scenarios
├── compliance-service/   # Wash-sale + funds compliance
├── ledger-service/       # Balances + positions
├── settlement-service/   # T+1 settlement scheduler
├── audit-service/        # Append-only audit log
├── saga-orchestrator/    # Distributed transaction coordinator
├── surge-simulator/      # Chaos engineering
├── equiflow-mcp/         # AI agent layer (Python, FastAPI, Claude API)
│   ├── api.py            # FastAPI server — /api/run, /api/seed, /api/cleanup
│   ├── agent.py          # Order triage agent
│   ├── compliance_agent.py
│   ├── duplicate_agent.py
│   ├── streaming_loop.py # Reusable async agent loop (SSE)
│   ├── equiflow_data_server.py  # Tool handlers + MCP server
│   ├── seed_duplicate_orders.py
│   ├── cleanup_scenario.py
│   ├── compare_duplicates.py
│   ├── MODES.md          # LIVE / LOCAL / MOCK run mode reference
│   └── fixtures/         # JSONL replay fixtures for MOCK mode
│       └── duplicate.jsonl
├── frontend/             # Agent visualization UI (React + Vite + Tailwind)
│   └── src/
│       ├── components/AgentRunner.tsx
│       ├── components/Timeline.tsx
│       └── types.ts
├── tests/
│   ├── e2e/              # Playwright API tests
│   └── performance/      # JMeter load tests
├── infra/
│   └── init-db/          # PostgreSQL database init scripts
├── docs/
│   └── SPEC.md           # Full product & technical specification
├── docker-compose.yml
├── .env.example
└── pom.xml               # Parent Maven POM
```

---

## Troubleshooting

**Services won't start**
```bash
docker-compose logs <service-name>   # check logs
docker-compose restart <service-name>
```

**Port already in use**
```bash
# Mac/Linux
lsof -i :<port> | grep LISTEN

# Windows (PowerShell)
netstat -ano | findstr :<port>
```

**Database migration errors**
```bash
# Wipe all data and restart clean
docker-compose down
docker-compose up --build
```

**Out of memory (Docker)**
Open Docker Desktop → Settings → Resources → increase Memory to at least **6 GB**.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.2, Spring Cloud 2023 |
| API Gateway | Spring Cloud Gateway |
| Message Bus | Apache Kafka (Confluent 7.5) |
| Database | PostgreSQL 16 |
| Cache | Redis 7 |
| Auth | JJWT 0.12 (HMAC-SHA256) |
| ORM | Spring Data JPA + Flyway |
| API Docs | springdoc-openapi 2.3 |
| Unit Tests | TestNG 7.9 + Mockito 5 |
| E2E Tests | Playwright |
| Performance | JMeter |
| Reporting | Allure |
| Build | Maven 3.9 (multi-module) |
| Runtime | Docker + Docker Compose |
| AI Agents | Python 3.11, Claude API (Anthropic), FastAPI, SSE |
| Agent UI | React 18, TypeScript, Vite, Tailwind CSS v4 |

---

## AI Agents

EquiFlow includes AI agents built on [MCP (Model Context Protocol)](https://modelcontextprotocol.io) and the Claude API. Agents connect directly to EquiFlow's live services and reason over real data to diagnose problems, summarise breaches, and surface issues that would otherwise require manual investigation.

All agents share a common reusable loop (`loop.py`) and a set of tool handlers (`equiflow_data_server.py`). Each agent file defines only what is specific to it: a system prompt, a tool list, and a dispatch table.

---

### Architecture

```
loop.py                    # run_agent() — the reusable agent loop, shared by all agents
equiflow_data_server.py    # tool handlers + MCP server (Claude Code integration)
agent.py                   # Order triage agent               (on-demand CLI)
compliance_agent.py        # Compliance breach agent          (on-demand CLI)
duplicate_agent.py         # Duplicate order detection agent  (on-demand CLI)
seed_duplicate_orders.py   # Duplicate scenario seed script   → writes scenario_pairs.json
cleanup_scenario.py        # Delete scenario test data between runs
compare_duplicates.py      # Reconcile seed pairs vs agent findings
```

**Adding a new agent:** create a new `*_agent.py` file. Import `run_agent` from `loop.py`, define `SYSTEM_TEMPLATE`, `TOOLS`, `DISPATCH`, `call_tool`, and `main`. The loop never changes.

---

### Tools

| Tool | Endpoint | Returns | Used by |
|---|---|---|---|
| `get_order` | `GET /orders/{id}` | Order status, saga ID, timestamps | triage, compliance |
| `get_saga` | `GET /sagas/{id}` | Step trace, failure reason, state | triage |
| `query_audit_log` | `GET /audit/events/order/{id}` | Retry history, last attempt time | triage |
| `list_orders` | `GET /orders/internal/all` | Paginated, filterable order list | triage, compliance, duplicate |
| `get_compliance_result` | `GET /compliance/results/order/{id}` | Violation type, reason, timestamp | compliance |

Auth is handled automatically — the server logs in as `bot-operator1` on first use and refreshes the token on expiry.

---

### Setup

**Prerequisites:** Python 3.11+, `ANTHROPIC_API_KEY` set in your environment, and the full stack running (`docker-compose up`).

**Install dependencies**

```bash
cd equiflow-mcp
pip install -r requirements.txt
```

**Set your API key**

```bash
export ANTHROPIC_API_KEY=sk-ant-...   # Mac/Linux
$env:ANTHROPIC_API_KEY="sk-ant-..."   # Windows PowerShell
```

---

### Agent 1 — Order Triage (on-demand)

Investigates a stuck or failed order and explains what went wrong in plain English.

```bash
python equiflow-mcp/agent.py "Order abc-123 has been stuck for 20 minutes. What's wrong?"
```

**What it does:**
1. `get_order` — confirms status, retrieves saga ID
2. `get_saga` — identifies which step failed and the failure reason
3. `query_audit_log` — checks retry count and last attempt time

**Example output:**
> Saga step 3 (settlement-service) failed with `INSUFFICIENT_FUNDS` at 14:23. The audit log shows 3 retry attempts — the last was 18 minutes ago with no further attempts. Recommend: verify the account balance has been funded, then manually trigger a settlement retry via `POST /admin/settlement/run`, or escalate to compliance if a wash-sale is involved.

To get a real order UUID:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"trader1","password":"password123"}' | jq -r .token)

curl -X POST http://localhost:8080/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"ticker":"AAPL","side":"BUY","type":"MARKET","quantity":10}'
```

---

### Agent 2 — Compliance Breach Summary (on-demand)

Summarises all compliance breaches for a given time period — breach count, violation breakdown, and repeat offenders — so a compliance officer can review them in one read.

```bash
python equiflow-mcp/compliance_agent.py "Show me today's compliance breaches"
python equiflow-mcp/compliance_agent.py "Which accounts have repeated wash-sale violations this week?"
```

**What it does:**
1. `list_orders(status=REJECTED, from=..., to=...)` — finds all rejected orders for the period
2. `get_compliance_result(order_id)` — retrieves violation type and reason for each
3. Groups by violation type, identifies repeat offenders, and summarises

**Example output:**
> 4 compliance breaches today. INSUFFICIENT_FUNDS: 3 (accounts trader1, trader2, trader3). WASH_SALE: 1 (account trader2 — repeat offender, 2 violations this week). Most recent: trader1 at 14:45, AAPL BUY rejected for insufficient funds ($2,400 short).

---

### Agent 3 — Duplicate Order Detection (on-demand)

Scans all orders in a time window for duplicate submissions — identical business fields, different UUIDs — and assigns a suspicion level based on how close together they were placed.

```bash
python equiflow-mcp/duplicate_agent.py "Scan today's orders for duplicates"
python equiflow-mcp/duplicate_agent.py "Check the last 2 hours for duplicate trades"
```

**What it does:**
1. `list_orders(from=..., to=..., size=100)` — fetches all orders for the window, paginates if needed
2. Groups by `(userId, ticker, side, quantity, limitPrice, type)` — any group with >1 order is a duplicate
3. Calculates time delta between earliest and latest order in each group; assigns suspicion level
4. Reports total pairs, per-pair detail, repeat offenders, and an overall assessment

**Suspicion levels:**

| Gap between orders | Level | Interpretation |
|---|---|---|
| < 5 seconds | HIGH | Almost certainly accidental — double-click or client retry |
| 5–30 seconds | MEDIUM | Possible API retry or client glitch |
| > 30 seconds | LOW | May be intentional; flag for human review |

**Overall assessment:** `CLEAR` (no duplicates) · `REVIEW` (MEDIUM/LOW only) · `ESCALATE` (any HIGH pair)

**Example output:**
```
Total duplicate pairs found: 1

| User       | Ticker | Side | Qty | Price  | Gap    | Suspicion | Original UUID        | Duplicate UUID       |
|------------|--------|------|-----|--------|--------|-----------|----------------------|----------------------|
| ...0001    | AMD    | BUY  | 5   | 419.53 | 1.08s  | HIGH      | 2575de17-...         | e8e93ba6-...         |

Repeat offenders: none
Assessment: ESCALATE
```

---

### Seed Scenario — Duplicate Orders

`equiflow-mcp/seed_duplicate_orders.py` populates the database with a configurable duplicate scenario so you can verify the agent end-to-end.

**How orders are placed:** the script authenticates as `trader1` and `trader2` using `POST /auth/token`, then calls `POST /orders` for each message. A duplicate is placed by re-sending the exact same JSON body the user last submitted — no special flag, no idempotency key. The server assigns a new UUID to each call and processes both orders independently through the full saga. The system does not detect the duplicate at write time; the agent detects it after the fact by grouping on business fields.

> **Market hours:** the seed script requires `market.hours.bypass=true` on `order-service`. Set `MARKET_HOURS_BYPASS=true` in your `.env` before running outside NYSE hours (9:30 AM–4:00 PM ET, weekdays).

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
| trader1 share | 60% × X | 60 messages |
| trader2 share | 40% × X | 40 messages |
| **Total seed time** | 9×333ms + 1s × 10 | **~13 s** |

> **Prices** are randomized 0.01–1000.00 per unique order. A duplicate re-sends the exact same price as its original, which is what makes it detectable.

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

Prices are random per run — the table below shows structure only.

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

Up to 10 pairs — all HIGH → agent outputs **ESCALATE**. (Actual count may be 9 if a duplicate position fires before that user's first unique order.)

**Scenario variants (randomized delay per pair):**

Each duplicate pair sleeps a random amount within the level's range — gaps vary across pairs rather than being uniform.

| Level | Delay range per pair | Suspicion | Expected assessment |
|---|---|---|---|
| HIGH | 1s–4s (random) | HIGH | ESCALATE |
| MED | 10s–25s (random) | MEDIUM | REVIEW |
| LOW | 60s–120s (random) | LOW | REVIEW |

**Run via CLI:**

```bash
# Requires: docker-compose up + MARKET_HOURS_BYPASS=true in .env

# 0. Clean previous test data (run any time between scenarios)
python equiflow-mcp/cleanup_scenario.py --execute

# 1. Seed — writes scenario_pairs.json on completion
#    --duplicate-delay sets min gap; --max-delay sets max (random per pair)
python equiflow-mcp/seed_duplicate_orders.py                                    # HIGH: 1s–4s
python equiflow-mcp/seed_duplicate_orders.py --duplicate-delay 10s --max-delay 25s   # MED
python equiflow-mcp/seed_duplicate_orders.py --duplicate-delay 60s --max-delay 120s  # LOW
python equiflow-mcp/seed_duplicate_orders.py --messages 10 --duplicate-pct 10        # quick run

# 2. Run the agent — writes agent_findings.json on completion
python equiflow-mcp/duplicate_agent.py "Scan today's orders for duplicates"

# 3. Reconcile seed vs agent
python equiflow-mcp/compare_duplicates.py
```

**Run via Agent UI** (see [Agent Visualization Frontend](#agent-visualization-frontend)):
- Click **Cleanup** to wipe test data
- Click **HIGH**, **MED**, or **LOW** to seed — runs can be stacked without cleanup between them
- Switch to "Duplicate Detection" and hit **RUN**

---

### Agent Visualization Frontend

A browser UI for running agents interactively, seeding test data, and watching the agent's reasoning step-by-step as it streams.

**Stack:** React 18 + TypeScript + Vite + Tailwind CSS v4. Communicates with the FastAPI backend (`api.py`) over SSE.

**Start the backend:**

```bash
cd equiflow-mcp
pip install -r requirements.txt
uvicorn api:app --port 8000
```

**Start the frontend:**

```bash
cd frontend
npm install
npm run dev
```

Open **http://localhost:5173**.

**Run modes:**

The `[LIVE][LOCAL][MOCK]` toggle in the header controls how each agent run executes. See [`equiflow-mcp/MODES.md`](equiflow-mcp/MODES.md) for full details.

| Mode | What runs | Cost | Use for |
|---|---|---|---|
| **LIVE** | Real DB + real Anthropic API | Tokens per run | Verifying LLM behavior, demos |
| **LOCAL** | Real DB + Python logic, no Anthropic | Free | Day-to-day dev (Duplicate Detection only) |
| **MOCK** | Fixture replay, no DB or API | Free | Pure UI work, no backend needed |

LIVE runs auto-save a fixture to `fixtures/{agent}.jsonl` on completion so MOCK has fresh data automatically.

**Layout:**

- **Sidebar** — agent list (Duplicate Detection, Compliance Monitor, Order Triage) with LIVE/SOON badges; global Test Data panel at the top
- **Test Data panel** — independent Cleanup and Seed buttons
  - **Cleanup** — deletes all non-Flyway orders and related records; safe to run any time
  - **HIGH / MED / LOW** — seeds duplicate order scenarios (stackable; multiple runs accumulate without cleanup)
- **Main panel** — agent description, example prompts, input box, and streaming timeline
- **Header** — `[LIVE][LOCAL][MOCK]` mode toggle; status bar with live pulse, tool call count, and token usage (`N in / N out tokens` for LIVE runs)
- **Timeline** — step-by-step view of each agent iteration: tool calls, tool results (expandable JSON), and the final answer rendered as a structured findings table (verdict banner + duplicate pairs table + repeat offenders)

**API endpoints (FastAPI):**

| Endpoint | Method | Body | Purpose |
|---|---|---|---|
| `/api/run` | POST | `{ agent, question, mode: "live"\|"local"\|"mock" }` | Stream agent events (SSE) |
| `/api/seed` | POST | `{ agent, level: "HIGH"\|"MED"\|"LOW" }` | Seed duplicate order scenario |
| `/api/cleanup` | POST | — | Delete all scenario test data, preserve Flyway seed |
| `/api/agents` | GET | — | List available agent IDs |

---

### Claude Code integration (MCP)

The MCP server (`equiflow_data_server.py`) exposes all tools to Claude Code so you can run triage queries directly in the chat without leaving your editor.

**1. Register the server**

The server is pre-configured in [.mcp.json](.mcp.json) and approved in [.claude/settings.json](.claude/settings.json). Claude Code detects it automatically when you open the project.

**2. Verify the connection**

```
/mcp
```

You should see `equiflow-data` listed with all 5 tools.

**3. Inspect the full schema**

```bash
npx @modelcontextprotocol/inspector python equiflow-mcp/equiflow_data_server.py
```

Opens a browser UI with every tool's description and input schema — exactly what the agent sees at runtime.

---

### How agents decide what to do

There is no hardcoded routing. Tool descriptions are what the agent reads at runtime to decide which tool to call and when. Each description encodes:

- **What it returns** — so the agent knows what data to expect
- **When to call it** — routing hints (`"Use this first"`, `"Use after list_orders"`)
- **Only for which inputs** — constraints that prevent wrong calls (`"Only call for REJECTED orders"`)

To change agent behaviour, edit the descriptions. To add a new tool, add a handler function, register it in `HANDLERS`, and add it to the relevant agent's `TOOLS` list.

---

### Handler structure

Each tool has its own handler. `call_tool` only routes:

```python
async def handle_get_order(args: dict) -> list[TextContent]:
    r = await authed_get(f"/orders/{args['order_id']}")
    if not r.is_success:
        return [TextContent(type="text", text=f"Error {r.status_code}: {r.text}")]
    return [TextContent(type="text", text=r.text)]

HANDLERS = {
    "get_order": handle_get_order,
    ...
}

@server.call_tool()
async def call_tool(name: str, arguments: dict) -> list[TextContent]:
    handler = HANDLERS.get(name)
    if handler is None:
        return [TextContent(type="text", text=f"Unknown tool: {name}")]
    return await handler(arguments)
```

Adding a new tool: write one handler function, add one line to `HANDLERS`. `call_tool` never changes.

---

### Agent Testing Architecture

Agents have two failure modes standard unit tests don't catch: loop failures (wrong message order, missed stop reason, infinite loop) and behavioral failures (agent calls the wrong tool, skips a required output section, follows the wrong decision branch). The test suite uses three tiers to cover both.

**Testing pyramid — where each tier sits:**

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
| **What runs real** | Loop logic, handler URL building | Loop code, dispatch, handler logic | Loop, dispatch, handlers, real services, real DB |
| **LLM called?** | No | No | No (golden file replay) |
| **Speed** | <1 ms | <100 ms | 10–30 s |
| **Cost** | Free | Free | API credits on record; free on replay |
| **Runs in CI?** | Yes | Yes | No |
| **When to run** | Every commit | Every PR | Before prompt changes, model upgrades |
| **What it catches** | Loop bugs, bad URL construction, error handling | Wrong tool sequence, missing output sections, bad branching | Real model regressions, stale tool return shapes |

**Key assertion rule (Tier 2 + 3):** assert facts, not exact strings. The model rephrases between runs — `assert "3" in answer` survives a model upgrade; `assert answer == "3 breaches detected"` does not.

```python
assert "3" in answer                 # breach count present
assert "WASH_SALE" in answer         # violation type named
assert "repeat" in answer.lower()    # repeat offender concept present
```

**Files:**

```
equiflow-mcp/
  tests/
    __init__.py
    fixtures/                          # JSON test data + golden conversation files
    test_loop.py                       # Tier 1 — loop mechanics
    test_handlers.py                   # Tier 1 — tool handler URL/error logic
    test_compliance_agent_behavior.py  # Tier 2 — compliance agent behavioral tests
    record_golden.py                   # Tier 3 — record a real conversation to fixture
    test_compliance_agent_e2e.py       # Tier 3 — replay golden fixture, assert facts
  requirements-test.txt
```

---

## Full Documentation

See [`docs/SPEC.md`](docs/SPEC.md) for the complete product and technical specification including all API endpoints with request/response examples.
