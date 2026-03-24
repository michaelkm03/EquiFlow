# EquiFlow

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
docker-compose down

# To also wipe all data and start fresh:
docker-compose down -v
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
# Wipe and restart clean
docker-compose down -v
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

---

## Full Documentation

See [`docs/SPEC.md`](docs/SPEC.md) for the complete product and technical specification including all API endpoints with request/response examples.
